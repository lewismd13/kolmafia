package net.sourceforge.kolmafia.persistence;

import static internal.helpers.Player.canInteract;
import static internal.helpers.Preference.isSetTo;
import static net.sourceforge.kolmafia.textui.command.AbstractCommandTestBase.assertErrorState;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasToString;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.RequestLogger;
import net.sourceforge.kolmafia.objectpool.ItemPool;
import net.sourceforge.kolmafia.objectpool.SkillPool;
import net.sourceforge.kolmafia.persistence.DailyLimitDatabase.DailyLimitType;
import net.sourceforge.kolmafia.preferences.Preferences;
import net.sourceforge.kolmafia.utilities.FileUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class DailyLimitDatabaseTest {
  @Nested
  class DailyLimitTests {
    @BeforeEach
    private void beforeEach() {
      KoLCharacter.reset("DailyLimitDatabaseTest");
      Preferences.reset("DailyLimitDatabaseTest");
    }

    @Test
    void canReadEntries() {
      // This list will grow and we don't want to keep having to updat the test,
      // but we can at the very least assert that it read a big chunk.
      assertThat(DailyLimitType.USE.getDailyLimits(), aMapWithSize(greaterThan(30)));
    }

    @Test
    void canReadBasicUseEntry() {
      var limit = DailyLimitType.USE.getDailyLimit(ItemPool.BALLAST_TURTLE);
      assertThat(limit.getId(), equalTo(ItemPool.BALLAST_TURTLE));
      assertThat(limit.getUses(), equalTo(0));
      assertThat(limit.getMax(), equalTo(1));
      assertThat(limit.getType(), equalTo(DailyLimitType.USE));
      assertThat(limit.getPref(), equalTo("_ballastTurtleUsed"));
    }

    @Test
    void canReadNonDefaultMaxUseEntry() {
      var limit = DailyLimitType.USE.getDailyLimit(ItemPool.CHOCOLATE_CIGAR);
      assertThat(limit.getId(), equalTo(ItemPool.CHOCOLATE_CIGAR));
      assertThat(limit.getUses(), equalTo(0));
      assertThat(limit.getMax(), equalTo(3));
      assertThat(limit.getType(), equalTo(DailyLimitType.USE));
      assertThat(limit.getPref(), equalTo("_chocolateCigarsUsed"));
    }

    @Test
    void canGetUsesRemaining() {
      Preferences.setBoolean("_floundryItemUsed", true);
      var limit = DailyLimitType.USE.getDailyLimit(ItemPool.FISH_HATCHET);

      assertThat(limit.getUsesRemaining(), equalTo(0));
    }

    @Test
    void canGetUsesRemainingForUsedProperty() {
      Preferences.setInteger("_feelDisappointedUsed", 1);
      var limit = DailyLimitType.CAST.getDailyLimit(SkillPool.FEEL_DISAPPOINTED);

      assertThat(limit.getUsesRemaining(), equalTo(2));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void canSeeIfAnyUsesRemaining(boolean expected) {
      Preferences.setInteger("goldenMrAccessories", 1);
      Preferences.setInteger("_smilesOfMrA", expected ? 3 : 5);

      var limit = DailyLimitType.CAST.getDailyLimit(SkillPool.SMILE_OF_MR_A);

      assertThat(limit.hasUsesRemaining(), equalTo(expected));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void correctlyLimitsTomes(final boolean interactive) {
      Preferences.setInteger("tomeSummons", 2);
      Preferences.setInteger("_stickerSummons", 1);

      // Summon Stickers
      var limit = DailyLimitType.CAST.getDailyLimit(7214);

      var cleanups = canInteract(interactive);
      try (cleanups) {
        assertThat(limit.getUses(), equalTo(interactive ? 1 : 2));
      }
    }

    @Test
    void canIncrementRegularEntries() {
      Preferences.setInteger("_jerksHealthMagazinesUsed", 3);

      var limit = DailyLimitType.USE.getDailyLimit(ItemPool.JERKS_HEALTH_MAGAZINE);
      limit.increment();

      assertThat("_jerksHealthMagazinesUsed", isSetTo(4));
    }

    @Test
    void canIncrementBooleanEntries() {
      Preferences.setBoolean("_jingleBellUsed", false);

      var limit = DailyLimitType.USE.getDailyLimit(ItemPool.JINGLE_BELL);
      limit.increment();

      assertThat("_jingleBellUsed", isSetTo(true));
    }

    @Test
    void canIncrementTomeEntries() {
      Preferences.setInteger("tomeSummons", 2);
      Preferences.setInteger("_radlibSummons", 1);

      var limit = DailyLimitType.CAST.getDailyLimit(SkillPool.RAD_LIB);
      limit.increment();

      assertThat("tomeSummons", isSetTo(3));
      assertThat("_radlibSummons", isSetTo(2));
    }
  }

  @Test
  void invalidDailyLimit() {
    var outputStream = new ByteArrayOutputStream();
    RequestLogger.openCustom(new PrintStream(outputStream));

    var contents = "Cast\tVisit Your Favourite Bird\t_favoriteBirdVisited\n";
    var reader = new BufferedReader(new StringReader(contents));

    try (var mock = Mockito.mockStatic(FileUtilities.class, Mockito.CALLS_REAL_METHODS)) {
      mock.when(
              () ->
                  FileUtilities.getVersionedReader(
                      "dailylimits.txt", KoLConstants.DAILYLIMITS_VERSION))
          .thenReturn(reader);
      DailyLimitDatabase.reset();
    }

    RequestLogger.closeCustom();

    assertThat(outputStream, hasToString(containsString("bogus")));
    assertErrorState();
  }

  @Test
  void invalidMax() {
    var outputStream = new ByteArrayOutputStream();
    RequestLogger.openCustom(new PrintStream(outputStream));

    var contents = "Cast\tVisit Your Favorite Bird\t_favoriteBirdVisited\tinvalid\n";
    var reader = new BufferedReader(new StringReader(contents));

    try (var mock = Mockito.mockStatic(FileUtilities.class, Mockito.CALLS_REAL_METHODS)) {
      mock.when(
              () ->
                  FileUtilities.getVersionedReader(
                      "dailylimits.txt", KoLConstants.DAILYLIMITS_VERSION))
          .thenReturn(reader);
      DailyLimitDatabase.reset();
    }

    RequestLogger.closeCustom();

    assertThat(outputStream, hasToString(containsString("invalid max")));
    assertErrorState();
  }

  @Test
  void invalidMaxExpression() {
    var outputStream = new ByteArrayOutputStream();
    RequestLogger.openCustom(new PrintStream(outputStream));

    var contents = "Cast\tVisit Your Favorite Bird\t_favoriteBirdVisited\t[a~a]\n";
    var reader = new BufferedReader(new StringReader(contents));

    try (var mock = Mockito.mockStatic(FileUtilities.class, Mockito.CALLS_REAL_METHODS)) {
      mock.when(
              () ->
                  FileUtilities.getVersionedReader(
                      "dailylimits.txt", KoLConstants.DAILYLIMITS_VERSION))
          .thenReturn(reader);
      DailyLimitDatabase.reset();
    }

    RequestLogger.closeCustom();

    assertThat(outputStream, hasToString(containsString("invalid max")));
    assertErrorState();
  }
}
