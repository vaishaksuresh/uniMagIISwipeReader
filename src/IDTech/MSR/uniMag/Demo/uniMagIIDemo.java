package IDTech.MSR.uniMag.Demo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import IDTech.MSR.XMLManager.StructConfigParameters;
import IDTech.MSR.uniMag.uniMagReader;
import IDTech.MSR.uniMag.uniMagReader.ReaderType;
import IDTech.MSR.uniMag.uniMagReaderMsg;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

// interface uniMagReaderMsg should be implemented
// if firmware download is supported, uniMagReaderToolsMsg also needs to be implemented 
public class uniMagIIDemo extends Activity implements uniMagReaderMsg {

	// declaring the instance of the uniMagReader;
	private uniMagReader myUniMagReader = null;
	// private uniMagSDKTools firmwareUpdateTool = null;

	private TextView connectStatusTextView; // displays status of UniMag Reader:
											// Connected / Disconnected
	private TextView headerTextView; // short description of data displayed
										// below
	private TextView textAreaTop;
	//private EditText textAreaBottom;
	//private Button btnCommand;
	private Button btnSwipeCard;
	private boolean isReaderConnected = false;
	private boolean isExitButtonPressed = false;
	private boolean isWaitingForCommandResult = false;
	private boolean isSaveLogOptionChecked = false;
	private boolean isUseAutoConfigProfileChecked = false;
	private boolean isConnectWithCommand = false;

	// update the powerup status
	private int percent = 0;
	private long beginTime = 0;
	// private byte[] challengeResponse = null;

	private String popupDialogMsg = null;
	private boolean enableSwipeCard = false;
	private boolean autoconfig_running = false;

	private String strMsrData = null;
	private byte[] msrData = null;
	private String statusText = null;
	// private int challengeResult = 0;

	private SQLiteDatabase myDb;
	private Cursor cursor;

	static private final String DB_NAME = "IDTECH.AutoConfig";
	static private final String DB_TABLE_PROFILE = "profiles";
	/*****************************************
	 * CREATE TABLE profiles ( search_date DATETIME, direction_output_wave
	 * INTEGER, input_frequency INTEGER, output_frequency INTEGER,
	 * record_buffer_size INTEGER, read_buffer_size INTEGER, wave_direction
	 * INTEGER, _low INTEGER, _high INTEGER, __low INTEGER, __high INTEGER,
	 * high_threshold INTEGER, low_threshold INTEGER, device_apm_base INTEGER,
	 * min INTEGER, max INTEGER, baud_rate INTEGER, preamble_factor INTEGER,
	 * set_default INTEGER)
	 * 
	 * )
	 *****************************************/

	static private final int REQUEST_GET_XML_FILE = 1;

	// property for the menu item.
	static final private int START_SWIPE_CARD = Menu.FIRST;
	static final private int SETTINGS_ITEM = Menu.FIRST + 2;
	static final private int SUB_SAVE_LOG_ITEM = Menu.FIRST + 3;
	static final private int SUB_USE_AUTOCONFIG_PROFILE = Menu.FIRST + 4;
	static final private int SUB_USE_COMMAND_TO_CONNECT = Menu.FIRST + 5;
	static final private int SUB_LOAD_XML = Menu.FIRST + 6;
	static final private int SUB_START_AUTOCONFIG = Menu.FIRST + 8;
	static final private int SUB_STOP_AUTOCONFIG = Menu.FIRST + 9;
	static final private int SUB_ATTACHED_TYPE = Menu.FIRST + 103;
	static final private int SUB_SUPPORT_STATUS = Menu.FIRST + 104;
	static final private int DELETE_LOG_ITEM = Menu.FIRST + 10;
	static final private int ABOUT_ITEM = Menu.FIRST + 11;
	static final private int EXIT_IDT_APP = Menu.FIRST + 12;

	private MenuItem itemStartSC = null;
	private MenuItem itemSubSaveLog = null;
	private MenuItem itemSubUseAutoConfigProfile = null;
	private MenuItem itemSubUseCommandToConnect = null;
	private MenuItem itemSubLoadXML = null;
	private MenuItem itemSubStartAutoConfig = null;
	private MenuItem itemSubStopAutoConfig = null;
	private MenuItem itemDelLogs = null;
	private MenuItem itemAbout = null;
	private MenuItem itemExitApp = null;

	private SubMenu sub = null;

	private UniMagTopDialog dlgTopShow = null;
	private UniMagTopDialog dlgSwipeTopShow = null;
	// private UniMagTopDialogYESNO dlgYESNOTopShow = null ;

	private StructConfigParameters profile = null;

	private Handler handler = new Handler();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		initializeDB();
		initializeUI();
		initializeReader();

		String strManufacture = myUniMagReader.getInfoManufacture();
		String strModel = myUniMagReader.getInfoModel();
		String strSDKVerInfo = myUniMagReader.getSDKVersionInfo();
		String strOSVerInfo = android.os.Build.VERSION.RELEASE;

		/*
		 * textAreaTop.setText("Phone: " + strManufacture + "\n" + "Model: " +
		 * strModel + "\n" + "SDK Ver: " + strSDKVerInfo + "\nOS Version: " +
		 * strOSVerInfo);
		 */
		textAreaTop.setText("Card Reader not Ready.");

		// to prevent screen timeout
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	@Override
	protected void onPause() {
		if (myUniMagReader != null) {
			// stop swipe card when the application go to background
			myUniMagReader.stopSwipeCard();
		}
		hideTopDialog();
		hideSwipeTopDialog();
		super.onPause();
	}

	@Override
	protected void onResume() {
		if (myUniMagReader != null) {
			if (isSaveLogOptionChecked == true)
				myUniMagReader.setSaveLogEnable(true);
			else
				myUniMagReader.setSaveLogEnable(false);
		}
		if (itemStartSC != null)
			itemStartSC.setEnabled(true);
		isWaitingForCommandResult = false;
		super.onResume();
	}

	@Override
	protected void onDestroy() {
		myUniMagReader.release();
		if (cursor != null)
			cursor.close();
		if (myDb != null)
			myDb.close();
		super.onDestroy();
		if (isExitButtonPressed) {
			android.os.Process.killProcess(android.os.Process.myPid());
		}
	}

	public synchronized void onActivityResult(final int requestCode,
			int resultCode, final Intent data) {

		if (resultCode == Activity.RESULT_OK) {

			String strTmpFileName = data.getStringExtra(FileDialog.RESULT_PATH);
			;
			if (requestCode == REQUEST_GET_XML_FILE) {

				if (!isFileExist(strTmpFileName)) {
					headerTextView.setText("Warning");
					textAreaTop
							.setText("Please copy the XML file 'IDT_uniMagCfg.xml' into root path of SD card.");
					//textAreaBottom.setText("");
					return;
				}

				// ///////////////////////////////////////////////////////////////////////////////
				// loadingConfigurationXMLFile() method may connect to server to
				// download xml file.
				// Network operation is prohibited in the UI Thread if target
				// API is 11 or above.
				// If target API is 11 or above, please use AsyncTask to avoid
				// errors.
				myUniMagReader.setXMLFileNameWithPath(strTmpFileName);
				myUniMagReader.loadingConfigurationXMLFile(false);
				// ///////////////////////////////////////////////////////////////////////////////

				headerTextView.setText("Status");
				textAreaTop.setText("Reload XML file succeeded.");
				//textAreaBottom.setText("");
			}
		}
	}

	private void initializeDB() {

		myDb = openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null);
		myDb.execSQL("CREATE TABLE IF NOT EXISTS " + DB_TABLE_PROFILE + "( "
				+ "search_date DATETIME, " + "direction_output_wave INTEGER, "
				+ "input_frequency INTEGER, " + "output_frequency INTEGER, "
				+ "record_buffer_size INTEGER, " + "read_buffer_size INTEGER, "
				+ "wave_direction INTEGER, " + "_low INTEGER, "
				+ "_high INTEGER, " + "__low INTEGER, " + "__high INTEGER, "
				+ "high_threshold INTEGER, " + "low_threshold INTEGER, "
				+ "device_apm_base INTEGER, " + "min INTEGER, "
				+ "max INTEGER, " + "baud_rate INTEGER, "
				+ "preamble_factor INTEGER, " + "set_default INTEGER)");

		isUseAutoConfigProfileChecked = useAutoConfigProfileAsDefault();
	}

	private void initializeUI() {
		btnSwipeCard = (Button) findViewById(R.id.btn_swipeCard);
		//btnCommand = (Button) findViewById(R.id.btn_command);
		textAreaTop = (TextView) findViewById(R.id.text_area_top);
		//textAreaBottom = (EditText) findViewById(R.id.text_area_bottom);
		connectStatusTextView = (TextView) findViewById(R.id.status_text);
		headerTextView = (TextView) findViewById(R.id.header_text);

		headerTextView.setText("Student ID");
		connectStatusTextView.setText("DISCONNECTED");
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// Set Listener for "Swipe Card" Button
		btnSwipeCard.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (myUniMagReader != null) {
					if (!isWaitingForCommandResult) {
						if (myUniMagReader.startSwipeCard()) {
							headerTextView.setText("Student ID");
							textAreaTop.setText("");
							//textAreaBottom.setText("");
							Log.d("Demo Info >>>>>", "to startSwipeCard");
						} else
							Log.d("Demo Info >>>>>", "cannot startSwipeCard");
					}
				}
			}
		});

		// Set Listener for "Command" Button
//		btnCommand.setOnClickListener(new OnClickListener() {
//			public void onClick(View v) {
//				if (!isWaitingForCommandResult) {
//					DlgSettingOption myDlg = new DlgSettingOption(
//							uniMagIIDemo.this, myUniMagReader);
//					myDlg.DisplayDlg();
//				}
//			}
//		});
	}

	private void initializeReader() {

		if (myUniMagReader != null) {
			myUniMagReader.unregisterListen();
			myUniMagReader.release();
			myUniMagReader = null;
		}
		if (isConnectWithCommand)
			myUniMagReader = new uniMagReader(this, this, true);
		else
			myUniMagReader = new uniMagReader(this, this);

		myUniMagReader.setVerboseLoggingEnable(true);
		myUniMagReader.registerListen();

		// load the XML configuratin file
		String fileNameWithPath = getXMLFileFromRaw();
		if (!isFileExist(fileNameWithPath)) {
			fileNameWithPath = null;
		}

		if (isUseAutoConfigProfileChecked) {
			if (updateProfileFromDB()) {
				Toast.makeText(this, "AutoConfig profile has been loaded.",
						Toast.LENGTH_SHORT).show();
				handler.post(doConnectUsingProfile);
			} else {
				Toast.makeText(this,
						"No profile found. Please run AutoConfig first.",
						Toast.LENGTH_SHORT).show();
			}
		} else {
			// ///////////////////////////////////////////////////////////////////////////////
			// Network operation is prohibited in the UI Thread if target API is
			// 11 or above.
			// If target API is 11 or above, please use AsyncTask to avoid
			// errors.
			myUniMagReader.setXMLFileNameWithPath(fileNameWithPath);
			myUniMagReader.loadingConfigurationXMLFile(true);
			// ///////////////////////////////////////////////////////////////////////////////
		}
		// Initializing SDKTool for firmware update
		// firmwareUpdateTool = new uniMagSDKTools(this,this);
		// firmwareUpdateTool.setUniMagReader(myUniMagReader);
		// myUniMagReader.setSDKToolProxy(firmwareUpdateTool.getSDKToolProxy());
	}

	// If 'idt_unimagcfg_default.xml' file is found in the 'raw' folder, it
	// returns the file path.
	private String getXMLFileFromRaw() {
		// the target filename in the application path
		String fileNameWithPath = null;
		fileNameWithPath = "idt_unimagcfg_default.xml";

		try {
			InputStream in = getResources().openRawResource(
					R.raw.idt_unimagcfg_default);
			int length = in.available();
			byte[] buffer = new byte[length];
			in.read(buffer);
			in.close();
			deleteFile(fileNameWithPath);
			FileOutputStream fout = openFileOutput(fileNameWithPath,
					MODE_PRIVATE);
			fout.write(buffer);
			fout.close();

			// to refer to the application path
			File fileDir = this.getFilesDir();
			fileNameWithPath = fileDir.getParent() + java.io.File.separator
					+ fileDir.getName();
			fileNameWithPath += java.io.File.separator
					+ "idt_unimagcfg_default.xml";

		} catch (Exception e) {
			e.printStackTrace();
			fileNameWithPath = null;
		}
		return fileNameWithPath;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK
				|| KeyEvent.KEYCODE_HOME == keyCode || KeyEvent.KEYCODE_SEARCH == keyCode)) {
			return false;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK
				|| KeyEvent.KEYCODE_HOME == keyCode || KeyEvent.KEYCODE_SEARCH == keyCode)) {

			return false;
		}
		return super.onKeyMultiple(keyCode, repeatCount, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK
				|| KeyEvent.KEYCODE_HOME == keyCode || KeyEvent.KEYCODE_SEARCH == keyCode)) {
			return false;
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		// when the 'swipe card' menu item clicked
		case (START_SWIPE_CARD): {
			headerTextView.setText("Student ID");
			textAreaTop.setText("");
			//textAreaBottom.setText("");
			// itemStartSC.setEnabled(false);

			if (myUniMagReader != null)
				myUniMagReader.startSwipeCard();
			break;
		}
		// when the 'exit' menu item clicked
		case (EXIT_IDT_APP): {
			isExitButtonPressed = true;
			if (myUniMagReader != null) {
				myUniMagReader.unregisterListen();
				myUniMagReader.stopSwipeCard();
				myUniMagReader.release();
			}
			finish();
			break;
		}
		// If save log option is already enabled, put a check mark whenever
		// settings menu is clicked.
		case (SETTINGS_ITEM): {
			if (itemSubSaveLog != null)
				itemSubSaveLog.setChecked(isSaveLogOptionChecked);
			if (itemSubUseAutoConfigProfile != null)
				itemSubUseAutoConfigProfile
						.setChecked(isUseAutoConfigProfileChecked);
			if (itemSubUseCommandToConnect != null)
				itemSubUseCommandToConnect.setChecked(isConnectWithCommand);
			break;
		}
		// deleting log files in the sd card.
		case (DELETE_LOG_ITEM): {
			if (myUniMagReader != null)
				myUniMagReader.deleteLogs();
			break;
		}
		// showing manufacturer, model number, SDK version, and OS Version
		// information if clicked.
		case (ABOUT_ITEM): {
			showAboutInfo();
			break;
		}
		// user can manually load a configuration file (xml), which should be
		// located in the sd card.
		case (SUB_LOAD_XML): {
			String strTmpFileName = getMyStorageFilePath();
			if (strTmpFileName == null) {
				headerTextView.setText("Warning");
				textAreaTop.setText("Please insert SD card.");
				//textAreaBottom.setText("");
				return false;
			}
			FileDialog fileDialog = new FileDialog();
			Intent intent = new Intent(getBaseContext(), fileDialog.getClass());
			intent.putExtra(FileDialog.START_PATH, Environment
					.getExternalStorageDirectory().getPath());
			startActivityForResult(intent, REQUEST_GET_XML_FILE);
			break;
		}
		case (SUB_START_AUTOCONFIG): {
			percent = 0;
			String fileNameWithPath = getXMLFileFromRaw();
			if (!isFileExist(fileNameWithPath)) {
				fileNameWithPath = null;
			}
			beginTime = getCurrentTime();
			myUniMagReader.startAutoConfig(fileNameWithPath, true);
			autoconfig_running = true;
			break;
		}
		case (SUB_STOP_AUTOCONFIG): {
			if (autoconfig_running == true) {
				myUniMagReader.stopAutoConfig();
				myUniMagReader.unregisterListen();
				myUniMagReader.release();

				percent = 0;
				// Reinitialize the reader if AutoConfig has been stopped.
				initializeReader();
				autoconfig_running = false;
			}
			break;
		}
		// when the 'save option' menu item clicked
		case (SUB_SAVE_LOG_ITEM): {
			if (item.isChecked()) {
				myUniMagReader.setSaveLogEnable(false);
				item.setChecked(false);
				isSaveLogOptionChecked = false;

			} else {
				// cannot enable the item when you are swiping the card.
				if (myUniMagReader.isSwipeCardRunning() == true) {
					item.setChecked(true);
					myUniMagReader.setSaveLogEnable(true);
					isSaveLogOptionChecked = true;
				}
			}
			break;
		}

		case (SUB_USE_AUTOCONFIG_PROFILE): {
			if (!isReaderConnected) {
				if (item.isChecked()) {
					item.setChecked(false);
					isUseAutoConfigProfileChecked = false;

					uncheckOnUseAutoConfigProfile();
					// change back to default profile
					initializeReader();

				} else {
					if (updateProfileFromDB()) {
						item.setChecked(true);
						isUseAutoConfigProfileChecked = true;
						checkOnUseAutoConfigProfile();
					} else {
						AlertDialog.Builder builder = new AlertDialog.Builder(
								this);
						builder.setTitle("Warning");
						builder.setMessage("No profile found. Please run AutoConfig first.");
						builder.setNegativeButton("OK",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int which) {
									}
								});
						AlertDialog alert = builder.create();
						alert.show();
					}
				}
			} else {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Warning");
				builder.setMessage("Please detach the reader in order to change a profile.");
				builder.setNegativeButton("OK",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
							}
						});
				AlertDialog alert = builder.create();
				alert.show();
			}

			break;
		}
		case (SUB_USE_COMMAND_TO_CONNECT): {
			if (!isReaderConnected) {
				if (item.isChecked()) {
					isConnectWithCommand = false;
					initializeReader();
				} else {
					// isConnectWithCommand = true;
					// initializeReader();

					AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setTitle("Caution");
					builder.setMessage("Please note that older generation of UniMag Readers (UniMag & UniMag Pro) won't be connected if this option checked.");
					builder.setNegativeButton("Cancel",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
								}
							});

					builder.setPositiveButton("OK",
							new DialogInterface.OnClickListener() {

								public void onClick(DialogInterface dialog,
										int which) {
									isConnectWithCommand = true;
									initializeReader();
								}
							});
					AlertDialog alert = builder.create();
					alert.show();
				}
			} else {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Information");
				builder.setMessage("Please detach the reader in order to change the setting.");
				builder.setNegativeButton("OK",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
							}
						});

				AlertDialog alert = builder.create();
				alert.show();
			}

			break;
		}
		// displays attached reader type
		case SUB_ATTACHED_TYPE:
			ReaderType art = myUniMagReader.getAttachedReaderType();
			textAreaTop.setText("Attached Reader:\n   " + getReaderName(art));
			//textAreaBottom.setText("");
			break;
		// displays support status of all ID Tech readers
		case SUB_SUPPORT_STATUS:
			// print a list of reader:supported status pairs
			textAreaTop.setText("Reader support status from cfg:\n");
			for (ReaderType rt : ReaderType.values()) {
				if (rt != ReaderType.UNKNOWN && rt != ReaderType.UM_OR_PRO)
					textAreaTop.append(getReaderName(rt) + " : "
							+ myUniMagReader.getSupportStatus(rt) + "\n");
			}
			//textAreaBottom.setText("");
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		itemStartSC = menu.add(0, START_SWIPE_CARD, Menu.NONE, "Swipe Card");
		itemStartSC.setEnabled(true);
		sub = menu.addSubMenu(0, SETTINGS_ITEM, Menu.NONE, "Settings");
		itemSubSaveLog = sub.add(0, SUB_SAVE_LOG_ITEM, Menu.NONE,
				"Save Log option");
		itemSubUseAutoConfigProfile = sub.add(1, SUB_USE_AUTOCONFIG_PROFILE,
				Menu.NONE, "Use AutoConfig profile");
		itemSubUseCommandToConnect = sub.add(1, SUB_USE_COMMAND_TO_CONNECT,
				Menu.NONE, "Command to Connect");
		itemSubLoadXML = sub.add(1, SUB_LOAD_XML, Menu.NONE, "Reload XML");
		itemSubStartAutoConfig = sub.add(4, SUB_START_AUTOCONFIG, Menu.NONE,
				"Start AutoConfig");
		itemSubStopAutoConfig = sub.add(5, SUB_STOP_AUTOCONFIG, Menu.NONE,
				"Stop AutoConfig");
		sub.add(Menu.NONE, SUB_ATTACHED_TYPE, Menu.NONE, "Get attached type");
		sub.add(Menu.NONE, SUB_SUPPORT_STATUS, Menu.NONE, "Get support status");
		itemSubSaveLog.setCheckable(true);
		itemSubUseAutoConfigProfile.setCheckable(true);
		itemSubUseCommandToConnect.setCheckable(true);
		itemSubLoadXML.setEnabled(true);
		itemSubStartAutoConfig.setEnabled(true);
		itemSubStopAutoConfig.setEnabled(true);
		itemDelLogs = menu.add(0, DELETE_LOG_ITEM, Menu.NONE, "Delete Logs");
		itemDelLogs.setEnabled(true);
		itemAbout = menu.add(0, ABOUT_ITEM, Menu.NONE, "About");
		itemAbout.setEnabled(true);
		itemExitApp = menu.add(0, EXIT_IDT_APP, Menu.NONE, "Exit");
		itemExitApp.setEnabled(true);
		return super.onCreateOptionsMenu(menu);
	}

	// Returns reader name based on abbreviations
	private String getReaderName(ReaderType rt) {
		switch (rt) {
		case UM:
			return "UniMag";
		case UM_PRO:
			return "UniMag Pro";
		case UM_II:
			return "UniMag II";
		case SHUTTLE:
			return "Shuttle";
		case UM_OR_PRO:
			return "UniMag or UniMag Pro";
		}
		return "Unknown";

	}

	private void showAboutInfo() {
		String strManufacture = myUniMagReader.getInfoManufacture();
		String strModel = myUniMagReader.getInfoModel();
		String strSDKVerInfo = myUniMagReader.getSDKVersionInfo();
		headerTextView.setText("SDK Info");
		//textAreaBottom.setText("");
		String strOSVerInfo = android.os.Build.VERSION.RELEASE;
		textAreaTop.setText("Phone: " + strManufacture + "\n" + "Model: "
				+ strModel + "\n" + "SDK Ver: " + strSDKVerInfo
				+ "\nOS Version: " + strOSVerInfo);

	}

	private Runnable doShowTimeoutMsg = new Runnable() {
		public void run() {
			if (itemStartSC != null && enableSwipeCard == true)
				itemStartSC.setEnabled(true);
			enableSwipeCard = false;
			showDialog(popupDialogMsg);
		}

	};

	// shows messages on the popup dialog
	private void showDialog(String strTitle) {
		try {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("UniMag");
			builder.setMessage(strTitle);
			builder.setPositiveButton("Ok",
					new DialogInterface.OnClickListener() {

						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					});
			builder.create().show();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	};

	private Runnable doShowTopDlg = new Runnable() {
		public void run() {
			showTopDialog(popupDialogMsg);
		}
	};
	private Runnable doHideTopDlg = new Runnable() {
		public void run() {
			hideTopDialog();
		}

	};
	private Runnable doShowSwipeTopDlg = new Runnable() {
		public void run() {
			showSwipeTopDialog();
		}
	};
	private Runnable doHideSwipeTopDlg = new Runnable() {
		public void run() {
			hideSwipeTopDialog();
		}
	};
	// displays result of commands, autoconfig, timeouts, firmware update
	// progress and results.
	private Runnable doUpdateStatus = new Runnable() {
		public void run() {
			try {
				textAreaTop.setText(statusText);
				headerTextView.setText("Status");
				if (msrData != null) {
					StringBuffer hexString = new StringBuffer();

					hexString.append("<");
					String fix = null;
					for (int i = 0; i < msrData.length; i++) {
						fix = Integer.toHexString(0xFF & msrData[i]);
						if (fix.length() == 1)
							fix = "0" + fix;
						hexString.append(fix);
						if ((i + 1) % 4 == 0 && i != (msrData.length - 1))
							hexString.append(' ');
					}
					hexString.append(">");
					//textAreaBottom.setText(hexString.toString());
				} else{
					//textAreaBottom.setText("");
				}
			} catch (Exception ex) {
				ex.printStackTrace();

			}
		}
	};
	/*
	 * // displays result of get challenge command private Runnable
	 * doUpdateChallengeData = new Runnable() { public void run() { try {
	 * textAreaTop.setText(statusText); headerTextView.setText("Command Info");
	 * if(cmdGetChallenge_Succeed_WithChallengData==challengeResult) {
	 * textAreaBottom.setText(""); textAreaBottom.setText(
	 * textAreaBottom.getText(), BufferType.EDITABLE);
	 * textAreaBottom.setEnabled(true); textAreaBottom.setClickable(true);
	 * textAreaBottom.setFocusable(true); } else if
	 * (cmdGetChallenge_Succeed_WithFileVersion==challengeResult) {
	 * textAreaBottom.setText(""); textAreaBottom.setText(
	 * textAreaBottom.getText(), BufferType.EDITABLE);
	 * textAreaBottom.setEnabled(true); textAreaBottom.setClickable(true);
	 * textAreaBottom.setFocusable(true); } else textAreaBottom.setText(""); }
	 * catch(Exception ex) { ex.printStackTrace();
	 * 
	 * } } };
	 */
	// displays data from card swiping
	private Runnable doUpdateTVS = new Runnable() {
		public void run() {
			try {
				if (itemStartSC != null)
					itemStartSC.setEnabled(true);
				textAreaTop.setText(strMsrData);

				StringBuffer hexString = new StringBuffer();
				hexString.append("<");
				String fix = null;
				for (int i = 0; i < msrData.length; i++) {
					fix = Integer.toHexString(0xFF & msrData[i]);
					if (fix.length() == 1)
						fix = "0" + fix;
					hexString.append(fix);
					if ((i + 1) % 4 == 0 && i != (msrData.length - 1))
						hexString.append(' ');
				}
				hexString.append(">");
				//textAreaBottom.setText(hexString.toString());
				adjustTextView();
				myUniMagReader.WriteLogIntoFile(hexString.toString());
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	};

	private void adjustTextView() {
//		int height = (textAreaTop.getHeight() + textAreaBottom.getHeight()) / 2;
//		textAreaTop.setHeight(height);
//		textAreaBottom.setHeight(height);
	}

	// displays a connection status of UniMag reader
	private Runnable doUpdateTV = new Runnable() {
		public void run() {
			if (!isReaderConnected)
				connectStatusTextView.setText("DISCONNECTED");
			else
				connectStatusTextView.setText("CONNECTED");
		}
	};
	private Runnable doUpdateToast = new Runnable() {
		public void run() {
			try {
				Context context = getApplicationContext();
				String msg = null;// "To start record the mic.";
				if (isReaderConnected) {
					msg = "<<CONNECTED>>";
					int duration = Toast.LENGTH_SHORT;
					Toast.makeText(context, msg, duration).show();
					if (itemStartSC != null)
						itemStartSC.setEnabled(true);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	};
	private Runnable doConnectUsingProfile = new Runnable() {
		public void run() {
			if (myUniMagReader != null)
				myUniMagReader.connectWithProfile(profile);
		}
	};

	/***
	 * Class: UniMagTopDialog Author: Eric Yang Date: 2010.10.12 Function: to
	 * show the dialog on the top of the desktop.
	 * 
	 * *****/
	private class UniMagTopDialog extends Dialog {

		public UniMagTopDialog(Context context) {
			super(context);
		}

		@Override
		public boolean onKeyDown(int keyCode, KeyEvent event) {
			if ((keyCode == KeyEvent.KEYCODE_BACK
					|| KeyEvent.KEYCODE_HOME == keyCode || KeyEvent.KEYCODE_SEARCH == keyCode)) {
				return false;
			}
			return super.onKeyDown(keyCode, event);
		}

		@Override
		public boolean onKeyMultiple(int keyCode, int repeatCount,
				KeyEvent event) {
			if ((keyCode == KeyEvent.KEYCODE_BACK
					|| KeyEvent.KEYCODE_HOME == keyCode || KeyEvent.KEYCODE_SEARCH == keyCode)) {

				return false;
			}
			return super.onKeyMultiple(keyCode, repeatCount, event);
		}

		@Override
		public boolean onKeyUp(int keyCode, KeyEvent event) {
			if ((keyCode == KeyEvent.KEYCODE_BACK
					|| KeyEvent.KEYCODE_HOME == keyCode || KeyEvent.KEYCODE_SEARCH == keyCode)) {
				return false;
			}
			return super.onKeyUp(keyCode, event);
		}
	}

	private class UniMagTopDialogYESNO extends Dialog {

		public UniMagTopDialogYESNO(Context context) {
			super(context);
		}

		@Override
		public boolean onKeyDown(int keyCode, KeyEvent event) {
			if ((keyCode == KeyEvent.KEYCODE_BACK
					|| KeyEvent.KEYCODE_HOME == keyCode || KeyEvent.KEYCODE_SEARCH == keyCode)) {
				return false;
			}
			return super.onKeyDown(keyCode, event);
		}

		@Override
		public boolean onKeyMultiple(int keyCode, int repeatCount,
				KeyEvent event) {
			if ((keyCode == KeyEvent.KEYCODE_BACK
					|| KeyEvent.KEYCODE_HOME == keyCode || KeyEvent.KEYCODE_SEARCH == keyCode)) {

				return false;
			}
			return super.onKeyMultiple(keyCode, repeatCount, event);
		}

		@Override
		public boolean onKeyUp(int keyCode, KeyEvent event) {
			if ((keyCode == KeyEvent.KEYCODE_BACK
					|| KeyEvent.KEYCODE_HOME == keyCode || KeyEvent.KEYCODE_SEARCH == keyCode)) {
				return false;
			}
			return super.onKeyUp(keyCode, event);
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {

		if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
			// you can make sure if you would change it
		}
		if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
			// you can make sure if you would change it
		}
		if (newConfig.keyboardHidden == Configuration.KEYBOARDHIDDEN_NO) {
			// you can make sure if you need change it
		}
		super.onConfigurationChanged(newConfig);
	}

	private void showTopDialog(String strTitle) {
		hideTopDialog();
		if (dlgTopShow == null)
			dlgTopShow = new UniMagTopDialog(this);
		try {
			Window win = dlgTopShow.getWindow();
			win.setFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND,
					WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
			dlgTopShow.setTitle("UniMag");
			dlgTopShow.setContentView(R.layout.dlgtopview);
			TextView myTV = (TextView) dlgTopShow.findViewById(R.id.TView_Info);

			myTV.setText(popupDialogMsg);
			dlgTopShow.setOnKeyListener(new OnKeyListener() {
				public boolean onKey(DialogInterface dialog, int keyCode,
						KeyEvent event) {
					if ((keyCode == KeyEvent.KEYCODE_BACK)) {
						return false;
					}
					return true;
				}
			});
			dlgTopShow.show();
		} catch (Exception ex) {
			ex.printStackTrace();
			dlgTopShow = null;
		}
	};

	private void hideTopDialog() {
		if (dlgTopShow != null) {
			try {
				dlgTopShow.hide();
				dlgTopShow.dismiss();
			} catch (Exception ex) {

				ex.printStackTrace();
			}
			dlgTopShow = null;
		}
	};

	private void showSwipeTopDialog() {
		hideSwipeTopDialog();
		try {

			if (dlgSwipeTopShow == null)
				dlgSwipeTopShow = new UniMagTopDialog(this);

			Window win = dlgSwipeTopShow.getWindow();
			win.setFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND,
					WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
			dlgSwipeTopShow.setTitle("UniMag");
			dlgSwipeTopShow.setContentView(R.layout.dlgswipetopview);
			TextView myTV = (TextView) dlgSwipeTopShow
					.findViewById(R.id.TView_Info);
			Button myBtn = (Button) dlgSwipeTopShow
					.findViewById(R.id.btnCancel);

			myTV.setText(popupDialogMsg);
			myBtn.setOnClickListener(new Button.OnClickListener() {
				public void onClick(View v) {
					if (itemStartSC != null)
						itemStartSC.setEnabled(true);
					// stop swipe
					myUniMagReader.stopSwipeCard();
					dlgSwipeTopShow.dismiss();
				}
			});
			dlgSwipeTopShow.setOnKeyListener(new OnKeyListener() {
				public boolean onKey(DialogInterface dialog, int keyCode,
						KeyEvent event) {
					if ((keyCode == KeyEvent.KEYCODE_BACK)) {
						return false;
					}
					return true;
				}
			});
			dlgSwipeTopShow.show();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	};

	/*
	 * private void showYesNoDialog( ) { hideSwipeTopDialog(); try{
	 * 
	 * if(dlgYESNOTopShow==null) dlgYESNOTopShow = new
	 * UniMagTopDialogYESNO(this);
	 * 
	 * Window win = dlgYESNOTopShow.getWindow();
	 * win.setFlags(WindowManager.LayoutParams
	 * .FLAG_DIM_BEHIND,WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
	 * dlgYESNOTopShow.setTitle("Warning");
	 * 
	 * dlgYESNOTopShow.setContentView(R.layout.dlgtopview2bnt ); TextView myTV =
	 * (TextView)dlgYESNOTopShow.findViewById(R.id.TView_Info);
	 * myTV.setTextColor(0xFF0FF000); Button myBtnYES =
	 * (Button)dlgYESNOTopShow.findViewById(R.id.btnYes); Button myBtnNO =
	 * (Button)dlgYESNOTopShow.findViewById(R.id.btnNo);
	 * 
	 * // myTV.setText(
	 * "Warrning, Now will Update Firmware if you press 'YES' to update, or 'No' to cancel"
	 * ); myTV.setText(
	 * "Upgrading the firmware might cause the device to not work properly. \nAre you sure you want to continue? "
	 * ); myBtnYES.setOnClickListener(new Button.OnClickListener() { public void
	 * onClick(View v) { //updateFirmware_exTools(); dlgYESNOTopShow.dismiss();
	 * } }); myBtnNO.setOnClickListener(new Button.OnClickListener() { public
	 * void onClick(View v) { dlgYESNOTopShow.dismiss(); } });
	 * dlgYESNOTopShow.setOnKeyListener(new OnKeyListener(){ public boolean
	 * onKey(DialogInterface dialog, int keyCode, KeyEvent event) { if ((keyCode
	 * == KeyEvent.KEYCODE_BACK)){ return false; } return true; } });
	 * dlgYESNOTopShow.show(); } catch(Exception ex) { ex.printStackTrace(); }
	 * };
	 */
	private void hideSwipeTopDialog() {
		try {
			if (dlgSwipeTopShow != null) {
				dlgSwipeTopShow.hide();
				dlgSwipeTopShow.dismiss();
				dlgSwipeTopShow = null;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	};

	// implementing a method onReceiveMsgCardData, defined in uniMagReaderMsg
	// interface
	// receiving card data here
	public void onReceiveMsgCardData(byte flagOfCardData, byte[] cardData) {
		byte flag = (byte) (flagOfCardData & 0x04);
		Log.d("Demo Info >>>>> onReceive flagOfCardData=" + flagOfCardData,
				"CardData=" + getHexStringFromBytes(cardData));

		strMsrData = new String(cardData).substring(1, 10);

		msrData = new byte[cardData.length];
		System.arraycopy(cardData, 0, msrData, 0, cardData.length);
		enableSwipeCard = true;
		handler.post(doHideTopDlg);
		handler.post(doHideSwipeTopDlg);
		handler.post(doUpdateTVS);
	}

	// implementing a method onReceiveMsgConnected, defined in uniMagReaderMsg
	// interface
	// receiving a message that the uniMag device has been connected
	public void onReceiveMsgConnected() {

		isReaderConnected = true;
		if (percent == 0)
			statusText = "Now the UniMag Unit is connected." + " ("
					+ getTimeInfoMs(beginTime) + "s)";
		else {
			statusText = "Now the UniMag Unit is connected." + " ("
					+ getTimeInfoMs(beginTime) + "s, " + "Profile found at "
					+ percent + "%)";
			percent = 0;
		}
		handler.post(doHideTopDlg);
		handler.post(doHideSwipeTopDlg);
		handler.post(doUpdateTV);
		handler.post(doUpdateToast);
		msrData = null;
		handler.post(doUpdateStatus);
	}

	private boolean updateProfileFromDB() {
		try {
			cursor = myDb.query(DB_TABLE_PROFILE, new String[] { "search_date",
					"direction_output_wave", "input_frequency",
					"output_frequency", "record_buffer_size",
					"read_buffer_size", "wave_direction", "_low", "_high",
					"__low", "__high", "high_threshold", "low_threshold",
					"device_apm_base", "min", "max", "baud_rate",
					"preamble_factor" }, null, null, null, null,
					"search_date desc");

			if (cursor.moveToFirst()) {
				profile = new StructConfigParameters();
				profile.setDirectionOutputWave((short) cursor.getInt(1));
				profile.setFrequenceInput(cursor.getInt(2));
				profile.setFrequenceOutput(cursor.getInt(3));
				profile.setRecordBufferSize(cursor.getInt(4));
				profile.setRecordReadBufferSize(cursor.getInt(5));
				profile.setWaveDirection(cursor.getInt(6));
				profile.set_Low((short) cursor.getInt(7));
				profile.set_High((short) cursor.getInt(8));
				profile.set__Low((short) cursor.getInt(9));
				profile.set__High((short) cursor.getInt(10));
				profile.sethighThreshold((short) cursor.getInt(11));
				profile.setlowThreshold((short) cursor.getInt(12));
				profile.setdevice_Apm_Base(cursor.getInt(13));
				profile.setMin((short) cursor.getInt(14));
				profile.setMax((short) cursor.getInt(15));
				profile.setBaudRate(cursor.getInt(16));
				profile.setPreAmbleFactor((short) cursor.getInt(17));
				Log.d("Demo>>updateProfileFromDB",
						"profile has been loaded from Database. Total of "
								+ cursor.getCount() + " rows stored.");
				cursor.close();

			} else {
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	// implementing a method onReceiveMsgDisconnected, defined in
	// uniMagReaderMsg interface
	// receiving a message that the uniMag device has been disconnected
	public void onReceiveMsgDisconnected() {
		isReaderConnected = false;
		isWaitingForCommandResult = false;
		handler.post(doHideTopDlg);
		handler.post(doHideSwipeTopDlg);
		handler.post(doUpdateTV);
		showAboutInfo();
	}

	// implementing a method onReceiveMsgTimeout, defined in uniMagReaderMsg
	// inteface
	// receiving a timeout message for powering up or card swipe
	public void onReceiveMsgTimeout(String strTimeoutMsg) {
		isWaitingForCommandResult = false;
		enableSwipeCard = true;
		handler.post(doHideTopDlg);
		handler.post(doHideSwipeTopDlg);
		statusText = strTimeoutMsg + "(" + getTimeInfo(beginTime) + ")";
		msrData = null;
		handler.post(doUpdateStatus);
	}

	// implementing a method onReceiveMsgToConnect, defined in uniMagReaderMsg
	// interface
	// receiving a message when SDK starts powering up the UniMag device
	public void onReceiveMsgToConnect() {
		beginTime = System.currentTimeMillis();
		handler.post(doHideTopDlg);
		handler.post(doHideSwipeTopDlg);
		popupDialogMsg = "Powering up uniMag...";
		handler.post(doShowTopDlg);
	}

	// implementing a method onReceiveMsgToSwipeCard, defined in uniMagReaderMsg
	// interface
	// receiving a message when SDK starts recording, then application should
	// ask user to swipe a card
	public void onReceiveMsgToSwipeCard() {
		handler.post(doHideTopDlg);
		handler.post(doHideSwipeTopDlg);
		popupDialogMsg = "Please swipe card.";
		handler.post(doShowSwipeTopDlg);
	}

	// this method has been depricated, and will not be called in this version
	// of SDK.
	public void onReceiveMsgSDCardDFailed(String strSDCardFailed) {
		popupDialogMsg = strSDCardFailed;
		handler.post(doHideTopDlg);
		handler.post(doHideSwipeTopDlg);
		handler.post(doShowTimeoutMsg);
	}

	// Setting a permission for user
	public boolean getUserGrant(int type, String strMessage) {
		Log.d("Demo Info >>>>> getUserGrant:", strMessage);
		boolean getUserGranted = false;
		switch (type) {
		case uniMagReaderMsg.typeToPowerupUniMag:
			// pop up dialog to get the user grant
			getUserGranted = true;
			break;
		case uniMagReaderMsg.typeToUpdateXML:
			// pop up dialog to get the user grant
			getUserGranted = true;
			break;
		case uniMagReaderMsg.typeToOverwriteXML:
			// pop up dialog to get the user grant
			getUserGranted = true;
			break;
		case uniMagReaderMsg.typeToReportToIdtech:
			// pop up dialog to get the user grant
			getUserGranted = true;
			break;
		default:
			getUserGranted = false;
			break;
		}
		return getUserGranted;
	}

	// implementing a method onReceiveMsgFailureInfo, defined in uniMagReaderMsg
	// interface
	// receiving a message when SDK could not find a profile of the phone
	public void onReceiveMsgFailureInfo(int index, String strMessage) {
		isWaitingForCommandResult = false;

		// If AutoConfig found a profile before and saved into db, then retreive
		// it and connect.
		if (updateProfileFromDB()) {
			showAboutInfo();
			handler.post(doConnectUsingProfile);
		} else {
			statusText = "This phone model is not on the supported phone list. Start/stop AutoConfig from the 'Settings' menu.";
			msrData = null;
			handler.post(doUpdateStatus);
		}
		// Cannot support current phone in the XML file.
		// start to Auto Config the parameters
		if (myUniMagReader.startAutoConfig(false) == true) {
			beginTime = getCurrentTime();
		}

	}

	// implementing a method onReceiveMsgCommandResult, defined in
	// uniMagReaderMsg interface
	// receiving a message when SDK is able to parse a response for commands
	// from the reader
	public void onReceiveMsgCommandResult(int commandID, byte[] cmdReturn) {
		Log.d("Demo Info >>>>> onReceive commandID=" + commandID, ",cmdReturn="
				+ getHexStringFromBytes(cmdReturn));
		isWaitingForCommandResult = false;
		switch (commandID) {
		case uniMagReaderMsg.cmdGetNextKSN:
			if (0 == cmdReturn[0])
				statusText = "Get Next KSN timeout.";
			else if (6 == cmdReturn[0])
				statusText = "Get Next KSN Succeed.";
			else
				statusText = "Get Next KSN failed.";
			break;
		case uniMagReaderMsg.cmdEnableAES:
			if (0 == cmdReturn[0])
				statusText = "Turn on AES timeout.";
			else if (6 == cmdReturn[0])
				statusText = "Turn on AES Succeed.";
			else
				statusText = "Turn on AES failed.";
			break;
		case uniMagReaderMsg.cmdEnableTDES:
			if (0 == cmdReturn[0])
				statusText = "Turn on TDES timeout.";
			else if (6 == cmdReturn[0])
				statusText = "Turn on TDES Succeed.";
			else
				statusText = "Turn on TDES failed.";
			break;
		case uniMagReaderMsg.cmdGetVersion:
			if (0 == cmdReturn[0])
				statusText = "Get Version timeout.";
			else if (6 == cmdReturn[0] && 2 == cmdReturn[1]
					&& 3 == cmdReturn[cmdReturn.length - 2]) {
				statusText = null;
				byte cmdDataX[] = new byte[cmdReturn.length - 4];
				System.arraycopy(cmdReturn, 2, cmdDataX, 0,
						cmdReturn.length - 4);
				statusText = "Get Version:" + new String(cmdDataX);
			} else {
				statusText = "Get Version failed, Error Format:<"
						+ getHexStringFromBytes(cmdReturn) + ">";
			}
			break;
		case uniMagReaderMsg.cmdGetSettings:
			if (0 == cmdReturn[0])
				statusText = "Get Setting timeout.";
			else if (6 == cmdReturn[0] && 2 == cmdReturn[1]
					&& 3 == cmdReturn[cmdReturn.length - 2]) {
				byte cmdDataX[] = new byte[cmdReturn.length - 4];
				System.arraycopy(cmdReturn, 2, cmdDataX, 0,
						cmdReturn.length - 4);
				statusText = "Get Setting:" + getHexStringFromBytes(cmdDataX);
				cmdDataX = null;
			} else {
				statusText = "Get Setting failed, Error Format:<"
						+ getHexStringFromBytes(cmdReturn) + ">";
			}
			break;
		case uniMagReaderMsg.cmdClearBuffer:
			if (0 == cmdReturn[0])
				statusText = "Clear Buffer timeout.";
			else if (6 == cmdReturn[0])
				statusText = "Clear Buffer Succeed.";
			else if (21 == cmdReturn[0])
				statusText = "Clear Buffer failed.";
			else {
				statusText = "Clear Buffer, Error Format:<"
						+ getHexStringFromBytes(cmdReturn) + ">";
			}
			break;

		default:
			break;
		}
		msrData = null;
		msrData = new byte[cmdReturn.length];
		System.arraycopy(cmdReturn, 0, msrData, 0, cmdReturn.length);
		handler.post(doUpdateStatus);
	}

	/*
	 * // implementing a method onReceiveMsgChallengeResult, defined in
	 * uniMagReaderToolsMsg interface // receiving a message when SDK is able to
	 * parse a response for get challenge command from the reader public void
	 * onReceiveMsgChallengeResult(int returnCode,byte[] data) {
	 * isWaitingForCommandResult = false; switch(returnCode) { case
	 * uniMagReaderToolsMsg.cmdGetChallenge_Succeed_WithChallengData:
	 * challengeResult = cmdGetChallenge_Succeed_WithChallengData; //show the
	 * challenge data and enable edit the hex text view
	 * if(6==data[0]&&2==data[1]&&3==data[data.length-2]) { statusText = null;
	 * byte cmdChallengeData[] = new byte[8]; System.arraycopy(data, 2,
	 * cmdChallengeData, 0, 8); byte cmdChallengeData_encyption[] = new byte[8];
	 * System.arraycopy(data, 2, cmdChallengeData_encyption, 0, 8);
	 * 
	 * byte cmdChallengeData_KSN[] = new byte[10]; System.arraycopy(data, 10,
	 * cmdChallengeData_KSN, 0, 10); statusText = "Challenge Data:<"+
	 * getHexStringFromBytes(cmdChallengeData)+"> "+"\n"+"KSN:<"+
	 * getHexStringFromBytes(cmdChallengeData_KSN)+">"+"\n"+
	 * "please enter your encrypted challenge 8 bytes hex data below and then update firmware."
	 * ; } else { statusText = "Get Challenge failed, Error Format:<"+
	 * getHexStringFromBytes(data)+">"; }
	 * 
	 * break; case uniMagReaderToolsMsg.cmdGetChallenge_Succeed_WithFileVersion:
	 * challengeResult = cmdGetChallenge_Succeed_WithFileVersion;
	 * if(6==data[0]&&((byte)0x56)==data[1] ) { statusText = null; byte
	 * cmdFileVersion[] = new byte[2]; System.arraycopy(data, 2, cmdFileVersion,
	 * 0, 2); char fileVersionHigh=(char) cmdFileVersion[0]; char
	 * fileVersionLow=(char) cmdFileVersion[1];
	 * 
	 * statusText =
	 * "Already in boot load mode, and the file version is "+fileVersionHigh
	 * +"."+fileVersionLow+"\n" + "Please update firmware directly."; } else {
	 * statusText = "Get Challenge failed, Error Format:<"+
	 * getHexStringFromBytes(data)+">"; }
	 * 
	 * break; case uniMagReaderToolsMsg.cmdGetChallenge_Failed: statusText =
	 * "Get Challenge failed, please try again.";
	 * 
	 * break; case uniMagReaderToolsMsg.cmdGetChallenge_NeedSetBinFile:
	 * statusText = "Get Challenge failed, need to set BIN file first."; break;
	 * case uniMagReaderToolsMsg.cmdGetChallenge_Timeout: statusText =
	 * "Get Challenge timeout."; break; default: break; } msrData = null;
	 * handler.post(doUpdateChallengeData);
	 * 
	 * }
	 */
	/*
	 * // implementing a method onReceiveMsgUpdateFirmwareProgress, defined in
	 * uniMagReaderToolsMsg interface // receiving a message of firmware update
	 * progress public void onReceiveMsgUpdateFirmwareProgress(int
	 * progressValue) { Log.d("Demo Info >>>>> UpdateFirmwareProgress"
	 * ,"v = "+progressValue); statusText =
	 * "Updating firmware, "+progressValue+"% finished."; msrData = null;
	 * handler.post(doUpdateStatus);
	 * 
	 * }
	 */
	/*
	 * // implementing a method onReceiveMsgUpdateFirmwareResult, defined in
	 * uniMagReaderToolsMsg interface // receiving a message when firmware
	 * update has been finished public void onReceiveMsgUpdateFirmwareResult(int
	 * result) { isWaitingForCommandResult = false;
	 * 
	 * switch(result) { case uniMagReaderToolsMsg.cmdUpdateFirmware_Succeed:
	 * statusText = "Update firmware succeed."; break; case
	 * uniMagReaderToolsMsg.cmdUpdateFirmware_NeedSetBinFile: statusText =
	 * "Update firmware failed, need to set BIN file first"; break; case
	 * uniMagReaderToolsMsg.cmdUpdateFirmware_NeedGetChallenge: statusText =
	 * "Update firmware failed, need to get challenge first."; break; case
	 * uniMagReaderToolsMsg.cmdUpdateFirmware_Need8BytesData: statusText =
	 * "Update firmware failed, need input 8 bytes data."; break; case
	 * uniMagReaderToolsMsg.cmdUpdateFirmware_Need24BytesData: statusText =
	 * "Update firmware failed, need input 24 bytes data."; break; case
	 * uniMagReaderToolsMsg.cmdUpdateFirmware_EnterBootloadModeFailed:
	 * statusText = "Update firmware failed, cannot enter boot load mode.";
	 * break; case uniMagReaderToolsMsg.cmdUpdateFirmware_DownloadBlockFailed:
	 * statusText = "Update firmware failed, cannot download block data.";
	 * break; case
	 * uniMagReaderToolsMsg.cmdUpdateFirmware_EndDownloadBlockFailed: statusText
	 * = "Update firmware failed, cannot end download block."; break; case
	 * uniMagReaderToolsMsg.cmdUpdateFirmware_Timeout: statusText =
	 * "Update firmware timeout."; break; }
	 * Log.d("Demo Info >>>>> UpdateFirmwareResult" ,"v = "+result); msrData =
	 * null; handler.post(doUpdateStatus);
	 * 
	 * }
	 */
	// implementing a method onReceiveMsgAutoConfigProgress, defined in
	// uniMagReaderMsg interface
	// receiving a message of Auto Config progress
	public void onReceiveMsgAutoConfigProgress(int progressValue) {
		Log.d("Demo Info >>>>> AutoConfigProgress", "v = " + progressValue);
		percent = progressValue;
		statusText = "Searching the configuration automatically, "
				+ progressValue + "% finished." + "(" + getTimeInfo(beginTime)
				+ ")";
		msrData = null;
		handler.post(doUpdateStatus);
	}

	public void onReceiveMsgAutoConfigCompleted(StructConfigParameters profile) {
		Log.d("Demo Info >>>>> AutoConfigCompleted",
				"A profile has been found, trying to connect...");
		autoconfig_running = false;
		this.profile = profile;
		handler.post(doInsertResultIntoDB);
		handler.post(doConnectUsingProfile);
	}

	Runnable doInsertResultIntoDB = new Runnable() {
		public void run() {
			if (profile == null)
				return;

			ContentValues insertValues = new ContentValues();

			SimpleDateFormat dateFormat = new SimpleDateFormat(
					"yyyy-MM-dd HH:mm:ss");
			Date date = new Date();
			insertValues.put("search_date", dateFormat.format(date));
			insertValues.put("direction_output_wave",
					profile.getDirectionOutputWave());
			insertValues.put("input_frequency", profile.getFrequenceInput());
			insertValues.put("output_frequency", profile.getFrequenceOutput());
			insertValues.put("record_buffer_size",
					profile.getRecordBufferSize());
			insertValues.put("read_buffer_size",
					profile.getRecordReadBufferSize());
			insertValues.put("wave_direction", profile.getWaveDirection());
			insertValues.put("_low", profile.get_Low());
			insertValues.put("_high", profile.get_High());
			insertValues.put("__low", profile.get__Low());
			insertValues.put("__high", profile.get__High());
			insertValues.put("high_threshold", profile.gethighThreshold());
			insertValues.put("low_threshold", profile.getlowThreshold());
			insertValues.put("device_apm_base", profile.getdevice_Apm_Base());
			insertValues.put("min", profile.getMin());
			insertValues.put("max", profile.getMax());
			insertValues.put("baud_rate", profile.getBaudRate());
			insertValues.put("preamble_factor", profile.getPreAmbleFactor());
			if (isUseAutoConfigProfileChecked) {
				insertValues.put("set_default", 1);
			} else {
				insertValues.put("set_default", 0);
			}

			try {
				// delete all previous profile
				myDb.execSQL("delete from " + DB_TABLE_PROFILE);
				myDb.insert(DB_TABLE_PROFILE, null, insertValues);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};

	public void checkOnUseAutoConfigProfile() {
		myDb.execSQL("update " + DB_TABLE_PROFILE + " set set_default='1'");
	}

	public void uncheckOnUseAutoConfigProfile() {
		myDb.execSQL("update " + DB_TABLE_PROFILE + " set set_default='0'");
	}

	public boolean isAutoConfigResultInDB() {
		try {
			cursor = myDb.query(DB_TABLE_PROFILE,
					new String[] { "set_default" }, null, null, null, null,
					"search_date desc");
			if (cursor.moveToFirst()) {
				cursor.close();
				return true;
			} else {
				if (cursor != null)
					cursor.close();
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;

	}

	public boolean useAutoConfigProfileAsDefault() {
		try {
			cursor = myDb.query(DB_TABLE_PROFILE,
					new String[] { "set_default" }, null, null, null, null,
					"search_date desc");
			if (cursor.moveToFirst()) {
				if (cursor.getInt(0) == 1) {
					cursor.close();
					return true;
				}
				if (cursor != null)
					cursor.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	/*
	 * public void getChallenge() { getChallenge_exTools(); } public void
	 * updateFirmware() { handler.post(doShowYESNOTopDlg); }
	 */
	/*
	 * private void getChallenge_exTools() { if (firmwareUpdateTool != null) {
	 * isWaitingForCommandResult = true; if (firmwareUpdateTool.getChallenge()
	 * == true) { // show to get challenge statusText =
	 * " To Get Challenge, waiting for response."; msrData = null;
	 * handler.post(doUpdateStatus); } } }
	 */
	/*
	 * private void updateFirmware_exTools() { if (firmwareUpdateTool != null) {
	 * String strData = textAreaBottom.getText().toString();
	 * 
	 * if(strData.length()>0) { challengeResponse =
	 * getBytesFromHexString(strData); if(challengeResponse==null) { statusText
	 * = "Invalidate challenge data, please input hex data."; msrData = null;
	 * handler.post(doUpdateStatus); return; } } else challengeResponse=null;
	 * 
	 * isWaitingForCommandResult = true; if
	 * (firmwareUpdateTool.updateFirmware(challengeResponse) == true) {
	 * statusText = " To Update Firmware, waiting for response."; msrData =
	 * null; handler.post(doUpdateStatus); } } }
	 */
	public void prepareToSendCommand(int cmdID) {
		isWaitingForCommandResult = true;
		switch (cmdID) {
		case uniMagReaderMsg.cmdGetNextKSN:
			statusText = " To Get Next KSN, waiting for response.";
			break;
		case uniMagReaderMsg.cmdEnableAES:
			statusText = " To Turn on AES, waiting for response.";
			break;
		case uniMagReaderMsg.cmdEnableTDES:
			statusText = " To Turn on TDES, waiting for response.";
			break;
		case uniMagReaderMsg.cmdGetVersion:
			statusText = " To Get Version, waiting for response.";
			break;
		case uniMagReaderMsg.cmdGetSettings:
			statusText = " To Get Setting, waiting for response.";
			break;
		case uniMagReaderMsg.cmdClearBuffer:
			statusText = " To Clear Buffer, waiting for response.";
			break;
		default:
			break;
		}
		msrData = null;
		handler.post(doUpdateStatus);
	}

	private String getHexStringFromBytes(byte[] data) {
		if (data.length <= 0)
			return null;
		StringBuffer hexString = new StringBuffer();
		String fix = null;
		for (int i = 0; i < data.length; i++) {
			fix = Integer.toHexString(0xFF & data[i]);
			if (fix.length() == 1)
				fix = "0" + fix;
			hexString.append(fix);
		}
		fix = null;
		fix = hexString.toString();
		return fix;
	}

	public byte[] getBytesFromHexString(String strHexData) {
		if (1 == strHexData.length() % 2) {
			return null;
		}
		byte[] bytes = new byte[strHexData.length() / 2];
		try {
			for (int i = 0; i < strHexData.length() / 2; i++) {
				bytes[i] = (byte) Integer.parseInt(
						strHexData.substring(i * 2, (i + 1) * 2), 16);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
		return bytes;
	}

	static private String getMyStorageFilePath() {
		String path = null;
		if (isStorageExist())
			path = Environment.getExternalStorageDirectory().toString();
		return path;
	}

	private boolean isFileExist(String path) {
		if (path == null)
			return false;
		File file = new File(path);
		if (!file.exists()) {
			return false;
		}
		return true;
	}

	static private boolean isStorageExist() {
		// if the SD card exists
		boolean sdCardExist = Environment.getExternalStorageState().equals(
				android.os.Environment.MEDIA_MOUNTED);
		return sdCardExist;
	}

	private long getCurrentTime() {
		return System.currentTimeMillis();
	}

	private String getTimeInfo(long timeBase) {
		int time = (int) (getCurrentTime() - timeBase) / 1000;
		int hour = (int) (time / 3600);
		int min = (int) (time / 60);
		int sec = (int) (time % 60);
		return hour + ":" + min + ":" + sec;
	}

	private String getTimeInfoMs(long timeBase) {
		float time = (float) (getCurrentTime() - timeBase) / 1000;
		String strtime = String.format("%03f", time);
		return strtime;
	}

}