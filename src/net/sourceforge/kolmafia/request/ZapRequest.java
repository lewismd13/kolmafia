/**
 * Copyright (c) 2005-2009, KoLmafia development team
 * http://kolmafia.sourceforge.net/
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "KoLmafia" nor the names of its contributors may
 *      be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.kolmafia.request;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.java.dev.spellcast.utilities.SortedListModel;

import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.RequestLogger;

import net.sourceforge.kolmafia.persistence.ItemDatabase;
import net.sourceforge.kolmafia.persistence.Preferences;
import net.sourceforge.kolmafia.session.InventoryManager;
import net.sourceforge.kolmafia.session.ResultProcessor;
import net.sourceforge.kolmafia.utilities.BooleanArray;
import net.sourceforge.kolmafia.utilities.FileUtilities;
import net.sourceforge.kolmafia.utilities.StringUtilities;

public class ZapRequest
	extends GenericRequest
{
	private static final Pattern ZAP_PATTERN = Pattern.compile( "whichitem=(\\d+)" );
	private static final Pattern OPTION_PATTERN = Pattern.compile( "<option value=(\\d+) descid='.*?'>.*?</option>" );

	private static final BooleanArray isZappable = new BooleanArray();
	private static final SortedListModel zappableItems = new SortedListModel();
	private static final HashMap zapGroups = new HashMap();

	private AdventureResult item;

	public ZapRequest( final AdventureResult item )
	{
		super( "wand.php" );

		this.item = null;

		if ( KoLCharacter.getZapper() == null )
		{
			return;
		}

		this.item = item;

		this.addFormField( "action", "zap" );
		this.addFormField( "whichwand", String.valueOf( KoLCharacter.getZapper().getItemId() ) );
		this.addFormField( "whichitem", String.valueOf( item.getItemId() ) );
	}

	private static final void initializeList()
	{
		if ( !ZapRequest.zappableItems.isEmpty() )
		{
			return;
		}

		try
		{
			String line;
			BufferedReader reader = FileUtilities.getVersionedReader( "zapgroups.txt", KoLConstants.ZAPGROUPS_VERSION );

			while ( ( line = FileUtilities.readLine( reader ) ) != null )
			{
				String[] list = line.split( "\\s*,\\s*" );
				for ( int i = 0; i < list.length; ++i )
				{
					int itemId = ItemDatabase.getItemId( list[ i ] );
					ZapRequest.zappableItems.add( new AdventureResult( itemId, 1 ) );
					ZapRequest.isZappable.set( itemId, true );
					ZapRequest.zapGroups.put( new Integer( itemId ), list );
				}
			}
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
	}

	public static final SortedListModel getZappableItems()
	{
		ZapRequest.initializeList();

		SortedListModel matchingItems = new SortedListModel();
		matchingItems.addAll( KoLConstants.inventory );
		if ( Preferences.getBoolean( "relayTrimsZapList" ) )
			matchingItems.retainAll( ZapRequest.zappableItems );
		return matchingItems;
	}
	
	public static final String[] getZapGroup( int itemId )
	{
		ZapRequest.initializeList();

		String[] rv = (String[]) ZapRequest.zapGroups.get( new Integer( itemId ) );
		if ( rv == null ) return new String[ 0 ];
		return rv;
	}

	public void run()
	{
		if ( this.item == null )
		{
			return;
		}

		if ( KoLCharacter.getZapper() == null )
		{
			KoLmafia.updateDisplay( KoLConstants.ERROR_STATE, "You don't have a wand." );
			return;
		}

		if ( InventoryManager.hasItem( this.item, true ) )
		{
			InventoryManager.retrieveItem( this.item );
		}

		if ( !KoLConstants.inventory.contains( this.item ) )
		{
			KoLmafia.updateDisplay( KoLConstants.ERROR_STATE, "You don't have a " + this.item.getName() + "." );
			return;
		}

		KoLmafia.updateDisplay( "Zapping " + this.item.getName() + "..." );
		super.run();
	}

	public void processResults()
	{
		// "The Crown of the Goblin King shudders for a moment, but
		// nothing happens."

		if ( this.responseText.indexOf( "nothing happens" ) != -1 )
		{
			ResultProcessor.processResult( this.item );
			KoLmafia.updateDisplay( KoLConstants.ERROR_STATE, "The " + this.item.getName() + " is not zappable." );
			return;
		}

		// If it blew up, remove wand
		if ( this.responseText.indexOf( "abruptly explodes" ) != -1 )
		{
			ResultProcessor.processResult( KoLCharacter.getZapper().getNegation() );
		}

		// Remove old item and notify the user of success.
		KoLmafia.updateDisplay( this.item.getName() + " has been transformed." );
	}

	public static final void decorate( final StringBuffer buffer )
	{
		// Don't trim the list if user wants to see all items
		if ( !Preferences.getBoolean( "relayTrimsZapList" ) )
			return;

		ZapRequest.initializeList();

		int selectIndex = buffer.indexOf( "<select" );
		if ( selectIndex == -1 )
			return;
		selectIndex = buffer.indexOf( ">", selectIndex ) + 1;
		int endSelectIndex = buffer.indexOf( "</select>", selectIndex );
		Matcher optionMatcher = ZapRequest.OPTION_PATTERN.matcher( buffer.substring( selectIndex, endSelectIndex ) );
		buffer.delete( selectIndex, endSelectIndex );

		int itemId;
		StringBuffer zappableOptions = new StringBuffer();
		while ( optionMatcher.find() )
		{
			itemId = Integer.parseInt( optionMatcher.group( 1 ) );
			if ( itemId == 0 || ZapRequest.isZappable.get( itemId ) )
			{
				zappableOptions.append( optionMatcher.group() );
			}
		}

		buffer.insert( selectIndex, zappableOptions.toString() );
	}

	public static final boolean registerRequest( final String urlString )
	{
		if ( !urlString.startsWith( "wand.php" ) )
		{
			return false;
		}

		Matcher itemMatcher = ZapRequest.ZAP_PATTERN.matcher( urlString );
		if ( !itemMatcher.find() )
		{
			return true;
		}

		int itemId = StringUtilities.parseInt( itemMatcher.group( 1 ) );
		AdventureResult item = new AdventureResult( itemId, -1 );

		RequestLogger.updateSessionLog();
		RequestLogger.updateSessionLog( "zap " + item.getName() );
		ResultProcessor.processResult( item );

		return true;
	}
}
