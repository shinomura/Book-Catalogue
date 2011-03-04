/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.ManagedTask.TaskHandler;
import com.eleybourn.bookcatalogue.UpdateFromInternet.FieldUsages.Usages;

public class UpdateFromInternet extends ActivityWithTasks {
	
	private UpdateThumbnailsThread mUpdateThread;

	/**
	 * Class to manage a collection of fields and the rules for importing them.
	 * Inherits from LinkedHashMap to guarantee iteration order.
	 * 
	 * @author Grunthos
	 */
	static public class FieldUsages extends LinkedHashMap<String,FieldUsage>  {
		static public enum Usages { COPY_IF_BLANK, ADD_EXTRA, OVERWRITE };

		public FieldUsage put(FieldUsage usage) {
			this.put(usage.fieldName, usage);
			return usage;
		}

	}
	private FieldUsages mFieldUsages = new FieldUsages();

	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.update_from_internet);
			setupFields();
		} catch (Exception e) {
			Logger.logError(e);
		}
	}
	
	public class FieldUsage {
		String fieldName;
		int stringId;
		Usages usage;
		boolean selected;
		FieldUsage(String name, int id, Usages usage) {
			this.fieldName = name;
			this.stringId = id;
			this.usage = usage;
			this.selected = true;
		}
	}
	
	/**
	 * This function builds the manage field visibility by adding onClick events
	 * to each field checkbox
	 */
	public void setupFields() {
		mFieldUsages.put(new FieldUsage(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, R.string.author, Usages.ADD_EXTRA));
		mFieldUsages.put(new FieldUsage(CatalogueDBAdapter.KEY_TITLE, R.string.title, Usages.COPY_IF_BLANK));
		mFieldUsages.put(new FieldUsage(CatalogueDBAdapter.KEY_THUMBNAIL, R.string.thumbnail, Usages.COPY_IF_BLANK));
		mFieldUsages.put(new FieldUsage(CatalogueDBAdapter.KEY_SERIES_ARRAY, R.string.series, Usages.ADD_EXTRA));
		mFieldUsages.put(new FieldUsage(CatalogueDBAdapter.KEY_PUBLISHER, R.string.publisher, Usages.COPY_IF_BLANK));
		mFieldUsages.put(new FieldUsage(CatalogueDBAdapter.KEY_DATE_PUBLISHED, R.string.date_published, Usages.COPY_IF_BLANK));
		mFieldUsages.put(new FieldUsage(CatalogueDBAdapter.KEY_PAGES, R.string.pages, Usages.COPY_IF_BLANK));
		mFieldUsages.put(new FieldUsage(CatalogueDBAdapter.KEY_LIST_PRICE, R.string.list_price, Usages.COPY_IF_BLANK));
		mFieldUsages.put(new FieldUsage(CatalogueDBAdapter.KEY_FORMAT, R.string.format, Usages.COPY_IF_BLANK));
		mFieldUsages.put(new FieldUsage(CatalogueDBAdapter.KEY_DESCRIPTION, R.string.description, Usages.COPY_IF_BLANK));
		mFieldUsages.put(new FieldUsage(CatalogueDBAdapter.KEY_GENRE, R.string.genre, Usages.COPY_IF_BLANK));
		
		// Display the list of fields
		LinearLayout parent = (LinearLayout) findViewById(R.id.manage_fields_scrollview);
		int i = 0;
		for(FieldUsage usage : mFieldUsages.values()) {
			//Create the LinearLayout to hold each row
			LinearLayout ll = new LinearLayout(this);
			ll.setPadding(5, 0, 0, 0);
			
			//Create the checkbox
			CheckBox cb = new CheckBox(this);
			cb.setChecked(usage.selected);
			cb.setTag(usage);
			cb.setId(R.id.fieldCheckbox);
			ll.addView(cb);

			//Create the checkBox label (or textView)
			TextView tv = new TextView(this);
			tv.setTextAppearance(this, android.R.attr.textAppearanceLarge);
			String text = getResources().getString(usage.stringId);
			String extra;
			switch(usage.usage) {
			case ADD_EXTRA:
				extra = getResources().getString(R.string.usage_add_extra);
				break;
			case COPY_IF_BLANK:
				extra = getResources().getString(R.string.usage_copy_if_blank);
				break;
			case OVERWRITE:
				extra = getResources().getString(R.string.usage_overwrite);
				break;
			default:
				throw new RuntimeException("Unknown Usage");
			}
			tv.setText(text + " (" + extra + ")");
			tv.setPadding(0, 5, 0, 0);
			ll.addView(tv);
			
			//Add the LinearLayout to the parent
			parent.addView(ll);			
		}

		Button confirmBtn = (Button) findViewById(R.id.confirm);
		confirmBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Get the selections the user made
				if (readUserSelections() == 0) {
					Toast.makeText(UpdateFromInternet.this, R.string.select_min_1_field, Toast.LENGTH_LONG).show();
					return;
				}

				// If they have selected thumbnails, check if they want to download ALL.
				if (mFieldUsages.get(CatalogueDBAdapter.KEY_THUMBNAIL).selected) {
					// Verify - this can be a dangerous operation
					AlertDialog alertDialog = new AlertDialog.Builder(UpdateFromInternet.this).setMessage(R.string.overwrite_thumbnail).create();
					alertDialog.setTitle(R.string.update_fields);
					alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
					alertDialog.setButton(UpdateFromInternet.this.getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							mFieldUsages.get(CatalogueDBAdapter.KEY_THUMBNAIL).usage = Usages.OVERWRITE;
							startUpdate();
							return;
						}
					}); 
					alertDialog.setButton2(UpdateFromInternet.this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							//do nothing
							return;
						}
					});
					alertDialog.setButton3(UpdateFromInternet.this.getResources().getString(R.string.no), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							mFieldUsages.get(CatalogueDBAdapter.KEY_THUMBNAIL).usage = Usages.COPY_IF_BLANK;
							startUpdate();
							return;
						}
					}); 
					alertDialog.show();					
				} else {
					startUpdate();					
				}
				return;
			}
		});

		Button cancelBtn = (Button) findViewById(R.id.cancel);
		cancelBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});
	}

	private int readUserSelections() {
		LinearLayout parent = (LinearLayout) findViewById(R.id.manage_fields_scrollview);
		int nChildren = parent.getChildCount();
		int nSelected = 0;
		for (int i = 0; i<nChildren; i++) {
			View v = parent.getChildAt(i);
			CheckBox cb = (CheckBox) v.findViewById(R.id.fieldCheckbox);
			if (cb != null) {
				FieldUsage usage = (FieldUsage) cb.getTag();
				usage.selected = cb.isChecked();
				if (usage.selected)
					nSelected++;
			}
		}	
		return nSelected;
	}

	private void startUpdate() {
		mUpdateThread = new UpdateThumbnailsThread(mTaskManager, mFieldUsages, mThumbnailsHandler);
		mUpdateThread.start();	
	}

	final UpdateThumbnailsThread.LookupHandler mThumbnailsHandler = new UpdateThumbnailsThread.LookupHandler() {
		@Override
		public void onFinish() {
			mUpdateThread = null;
		}
	};

	@Override
	TaskHandler getTaskHandler(ManagedTask t) {
		if (t instanceof UpdateThumbnailsThread)
			return mThumbnailsHandler;
		else
			if (mUpdateThread != null)
				return mUpdateThread.getTaskHandler(t);
			else
				return null;
	}

	/**
	 * Ensure the mUpdateThread is restored.
	 */
	@Override
	protected void onRestoreInstanceState(Bundle inState) {
		mUpdateThread = (UpdateThumbnailsThread) getLastNonConfigurationInstance("UpdateThread");
		// Call the super method only after we have the searchManager set up
		super.onRestoreInstanceState(inState);
	}

	/**
	 * Ensure the TaskManager is saved.
	 */
	@Override
	public void onRetainNonConfigurationInstance(Hashtable<String,Object> store) {
		if (mUpdateThread != null) {
			store.put("UpdateThread", mUpdateThread);
			mUpdateThread = null;
		}
	}
}