/*
 * Copyright (c) 2016.
 *
 * DReflect and Minuku Libraries by Shriti Raj (shritir@umich.edu) and Neeraj Kumar(neerajk@uci.edu) is licensed under a Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License.
 * Based on a work at https://github.com/Shriti-UCI/Minuku-2.
 *
 *
 * You are free to (only if you meet the terms mentioned below) :
 *
 * Share — copy and redistribute the material in any medium or format
 * Adapt — remix, transform, and build upon the material
 *
 * The licensor cannot revoke these freedoms as long as you follow the license terms.
 *
 * Under the following terms:
 *
 * Attribution — You must give appropriate credit, provide a link to the license, and indicate if changes were made. You may do so in any reasonable manner, but not in any way that suggests the licensor endorses you or your use.
 * NonCommercial — You may not use the material for commercial purposes.
 * ShareAlike — If you remix, transform, or build upon the material, you must distribute your contributions under the same license as the original.
 * No additional restrictions — You may not apply legal terms or technological measures that legally restrict others from doing anything the license permits.
 */

package edu.ohio.minuku_2;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import edu.ohio.minuku.Data.DBHelper;
import edu.ohio.minuku.Utilities.ScheduleAndSampleManager;
import edu.ohio.minuku.config.Constants;
import edu.ohio.minuku.event.DecrementLoadingProcessCountEvent;
import edu.ohio.minuku.event.IncrementLoadingProcessCountEvent;
import edu.ohio.minuku.logger.Log;
import edu.ohio.minuku.manager.DBManager;
import edu.ohio.minuku.service.TransportationModeService;
import edu.ohio.minuku_2.controller.Ohio.SurveyActivity;
import edu.ohio.minuku_2.controller.Ohio.recordinglistohio;
import edu.ohio.minuku_2.controller.Ohio.sleepingohio;
import edu.ohio.minuku_2.controller.report;
import edu.ohio.minuku_2.service.BackgroundService;
import edu.ohio.minuku_2.service.SurveyTriggerService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    //private TextView compensationMessage;

    private AtomicInteger loadingProcessCount = new AtomicInteger(0);
    private ProgressDialog loadingProgressDialog;

    private int mYear, mMonth, mDay;

    public static String task="PART"; //default is PART
    ArrayList viewList;
    public final int REQUEST_ID_MULTIPLE_PERMISSIONS=1;
    public static View timerview,recordview,deviceIdview;

    public static android.support.design.widget.TabLayout mTabs;
    public static ViewPager mViewPager;

    private TextView device_id;
    private TextView num_6_digit;
    private TextView user_id;
    private TextView sleepingtime;

    private ImageView tripStatus;
    private ImageView surveyStatus;

    private Button ohio_setting, ohio_annotate, startservice, tolinkList;
    private String projName = "Ohio";

    private int requestCode_setting = 1;
    private Bundle requestCode_annotate;

    private boolean firstTimeToShowDialogOrNot;
    private SharedPreferences sharedPrefs;

    private Handler handler;

    private ScheduledExecutorService mScheduledExecutorService;
    public static final int REFRESH_FREQUENCY = 3; //10s, 10000ms
    public static final int BACKGROUND_RECORDING_INITIAL_DELAY = 0;
    //private UserSubmissionStats mUserSubmissionStats;

//    private final String checkInUrl = "http://mcog.asc.ohio-state.edu/apps/servicerec?";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Creating Main activity");

        Log.e(TAG,"start");

        //** Please set your project name. **//
        settingHomepageView();

        handler = new Handler();

        setServiceNotKilled();

        startService(new Intent(getBaseContext(), BackgroundService.class));

        EventBus.getDefault().register(this);

        int sdk_int = Build.VERSION.SDK_INT;
        if(sdk_int>=23) {
            checkAndRequestPermissions();
        }else{
            startServiceWork();
        }

    }

    public void createShortCut(){
        Intent shortcutintent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        shortcutintent.putExtra("duplicate", false);
        shortcutintent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.app_name));
        Parcelable icon = Intent.ShortcutIconResource.fromContext(getApplicationContext(), R.drawable.dms_icon);
        shortcutintent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon);
        shortcutintent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, new Intent(getApplicationContext(), SplashScreen.class));
        sendBroadcast(shortcutintent);
    }


    public static String getTimeString(long time){

        SimpleDateFormat sdf_now = new SimpleDateFormat(Constants.DATE_FORMAT_NOW_SLASH);
        String currentTimeString = sdf_now.format(time);

        return currentTimeString;
    }

    public void getStartDate(){
        //get timzone
//        TimeZone tz = TimeZone.getDefault();
        Calendar cal = Calendar.getInstance();
        Date date = new Date();
        cal.setTime(date);
        int Year = cal.get(Calendar.YEAR);
        int Month = cal.get(Calendar.MONTH)+1;
        int Day = cal.get(Calendar.DAY_OF_MONTH);

        int Hour = cal.get(Calendar.HOUR_OF_DAY);
        int Min = cal.get(Calendar.MINUTE);
        Log.d(TAG, "Year : "+Year+" Month : "+Month+" Day : "+Day+" Hour : "+Hour+" Min : "+Min);

        sharedPrefs.edit().putInt("StartYear", Year).apply();
        sharedPrefs.edit().putInt("StartMonth", Month).apply();
        sharedPrefs.edit().putInt("StartDay", Day).apply();

        sharedPrefs.edit().putInt("StartHour", Hour).apply();
        sharedPrefs.edit().putInt("StartMin", Min).apply();

        Log.d(TAG, "Start Year : " + Year + " Month : " + Month + " Day : " + Day + " daysInSurvey : " + Constants.daysInSurvey);

    }

    private void settingHomepageView(){

        requestCode_annotate = new Bundle();

        mScheduledExecutorService = Executors.newScheduledThreadPool(REFRESH_FREQUENCY);

        setContentView(R.layout.homepage);

        /* alertdialog for checking userid */
        sharedPrefs = getSharedPreferences("edu.umich.minuku_2", MODE_PRIVATE);

        sharedPrefs.edit().putBoolean("resetIntervalSurveyFlag", false).apply();

        firstTimeToShowDialogOrNot = sharedPrefs.getBoolean("firstTimeToShowDialogOrNot", true);
        Log.d(TAG,"firstTimeToShowDialogOrNot : "+firstTimeToShowDialogOrNot);

        Constants.USER_ID = sharedPrefs.getString("userid","NA");

        if(firstTimeToShowDialogOrNot) {
            createShortCut(); //on home Screen Desktop
            showdialogforuser();
        }else if(Constants.USER_ID.equals("NA")){
            showdialogforuser();
        }

        user_id = (TextView) findViewById(R.id.userid);
        Constants.USER_ID = sharedPrefs.getString("userid","NA");
        user_id.setText("Confirmation #:" );

        num_6_digit = (TextView) findViewById(R.id.group_num);
        Constants.GROUP_NUM =  sharedPrefs.getString("groupNum","NA");
        num_6_digit.setText(Constants.USER_ID);

        Constants.daysInSurvey = sharedPrefs.getInt("daysInSurvey",0);

        //button
        tolinkList = (Button) findViewById(R.id.linkList);
        tolinkList.setOnClickListener(new Button.OnClickListener(){

            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, SurveyActivity.class));

            }
        });

        ohio_setting = (Button)findViewById(R.id.Setting);
        ohio_setting.setOnClickListener(ohio_settinging);

        ohio_annotate = (Button)findViewById(R.id.Annotate);
        ohio_annotate.setOnClickListener(ohio_annotateing);

        //TODO deprecated
            /*startservice = (Button)findViewById(R.id.service);
            startservice.setOnClickListener(new Button.OnClickListener(){

                @Override
                public void onClick(View v) {

                    //Maybe useless in this project.
//                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));  // 協助工具

                    Intent intent1 = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);  //usage
                    startActivity(intent1);

//                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS); //notification
//                    startActivity(intent);

                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));	//location

                }

            });*/

        sleepingtime = (TextView)findViewById(R.id.sleepingTime);
        String sleepStartTime = sharedPrefs.getString("SleepingStartTime","Please select your sleeping time");
        String sleepEndTime = sharedPrefs.getString("SleepingEndTime","Please select your wake up time");

        if(!sleepStartTime.equals("Please select your sleeping time")&&!sleepEndTime.equals("Please select your wake up time")){

            sleepingtime.setText("Sleep: "+sleepStartTime+" to "+sleepEndTime);
        }
        else
            sleepingtime.setText("Set Sleep Time");
    }

    private void setServiceNotKilled() {
        Log.d("FFF", "toggleNotificationListenerService");
        PackageManager pm = getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName(this, BackgroundService.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(new ComponentName(this, BackgroundService.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        pm.setComponentEnabledSetting(new ComponentName(this, SurveyTriggerService.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(new ComponentName(this, SurveyTriggerService.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        pm.setComponentEnabledSetting(new ComponentName(this, TransportationModeService.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(new ComponentName(this, TransportationModeService.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }

    private void startTimerThread() {
        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(10 * 60000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    handler.post(statusRunnable);
                }
            }
        };

        new Thread(runnable).start();
    }

    Runnable statusRunnable = new Runnable() {
        @Override
        public void run() {

            Log.d(TAG, "statusRunnable");

            ArrayList<String> data = new ArrayList<String>();
            ArrayList<Integer> dataPos = new ArrayList<Integer>();
            String taskTable = null;
            Calendar cal = Calendar.getInstance();
            Date date = new Date();
            cal.setTime(date);
            int Year = cal.get(Calendar.YEAR);
            int Month = cal.get(Calendar.MONTH) + 1;
            int Day = cal.get(Calendar.DAY_OF_MONTH);

            String startTimeString = makingDataFormat(Year, Month, Day);
            String endTimeString = makingDataFormat(Year, Month, Day + 1);
            long startTime = getSpecialTimeInMillis(startTimeString);
            long endTime = getSpecialTimeInMillis(endTimeString);

            ArrayList<String> results = DBHelper.querySessionsBetweenTimes(startTime, endTime);
            int countForLight = 0;
            for(String result : results){
                String annotation = result.split(Constants.DELIMITER)[4];
                if(!annotation.equals("")){
                    countForLight++;
                }
            }

            Drawable drawable_red = getResources().getDrawable(R.drawable.circle_red);
            drawable_red.setBounds(0, 0, (int)(drawable_red.getIntrinsicWidth()*0.5), (int)(drawable_red.getIntrinsicHeight()*0.5));

            Drawable drawable_green = getResources().getDrawable(R.drawable.circle_green);
            drawable_green.setBounds(0, 0, (int)(drawable_green.getIntrinsicWidth()*0.5), (int)(drawable_green.getIntrinsicHeight()*0.5));


            if(countForLight!=results.size()){
                ohio_annotate.setCompoundDrawables(null, null, drawable_red, null);
            }else {
                ohio_annotate.setCompoundDrawables(null, null, drawable_green, null);
            }

            dataPos = new ArrayList<Integer>();

            taskTable = DBHelper.surveyLink_table;

            int tripCursorNum = -1;

            try {
                SQLiteDatabase db = DBManager.getInstance().openDatabase();

                Cursor tripCursor = db.rawQuery("SELECT " + DBHelper.openFlag_col + ", " + DBHelper.link_col + " FROM " + taskTable + " WHERE " //+ DBHelper.Trip_id + " ='" + position + "'" +" AND "
                        + DBHelper.TIME + " BETWEEN" + " '" + startTime + "' " + "AND" + " '" + endTime + "' ORDER BY " + DBHelper.TIME + " DESC", null);

                Log.d(TAG, "SELECT " + DBHelper.openFlag_col + ", " + DBHelper.link_col + " FROM " + taskTable + " WHERE " //+ DBHelper.Trip_id + " ='" + position + "'" +" AND "
                        + DBHelper.TIME + " BETWEEN" + " '" + startTime + "' " + "AND" + " '" + endTime + "' ORDER BY " + DBHelper.TIME + " DESC");

                tripCursorNum = tripCursor.getCount()-1;
                //get all data from cursor
                int i = 0;
                if (tripCursor.moveToFirst()) {
                    do {
                        int eachdataInCursor = tripCursor.getInt(0);
                        String link = tripCursor.getString(1);

                        Log.d(TAG, " 0 : " + eachdataInCursor + ", 1 : " + link);

                        if (eachdataInCursor == 0) { //0 represent they haven't click into it.
                            dataPos.add(i);
                        }
                        i++;
                        data.add(link);
                        Log.d(TAG, " link : " + link);

                        Log.d(TAG, " tripCursor.moveToFirst()");
                    } while (tripCursor.moveToNext());
                } else
                    Log.d(TAG, " tripCursor.moveToFirst() else");
                tripCursor.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (!dataPos.isEmpty()) {
                tolinkList.setCompoundDrawables(null, null, drawable_red, null);
                sharedPrefs.edit().putBoolean("SurveyStatus", true).apply();
            } else {
                tolinkList.setCompoundDrawables(null, null, drawable_green, null);
                sharedPrefs.edit().putBoolean("SurveyStatus", false).apply();

            }
        }
    };

    private void startSettingSleepingTime(){
        Intent intent = new Intent(this, sleepingohio.class);  //usage
        startActivity(intent);
    }

    public void startpermission(){
        //Maybe useless in this project.
//                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));  // 協助工具

        Intent intent1 = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);  //usage
        startActivity(intent1);

//                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS); //notification
//                    startActivity(intent);

        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));	//location
    }

    private void showdialogforuser(){

        //** User ID = Confirmation Number

        firstTimeToShowDialogOrNot = false;

        sharedPrefs = getSharedPreferences("edu.umich.minuku_2", MODE_PRIVATE);
        sharedPrefs.edit().putBoolean("firstTimeToShowDialogOrNot", firstTimeToShowDialogOrNot).apply();

        LinearLayout layout = new LinearLayout(MainActivity.this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        //the parameter for the size of the editText
        TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT,1.0f);

        final EditText editText_confirmNum = new EditText(MainActivity.this);
        editText_confirmNum.setHint("Confirmation number");
        editText_confirmNum.setGravity(Gravity.CENTER_HORIZONTAL);
        editText_confirmNum.setLayoutParams(params);
        editText_confirmNum.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(6)
        });
        layout.addView(editText_confirmNum);

        final EditText editText_Email = new EditText(MainActivity.this);
        editText_Email.setHint("your Email");
        editText_Email.setGravity(Gravity.CENTER_HORIZONTAL);
        editText_Email.setLayoutParams(params);
        layout.addView(editText_Email);

        user_id = (TextView) findViewById(R.id.userid);
        num_6_digit = (TextView) findViewById(R.id.group_num);


        AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle("Please fill in your confirmation number and Email.")
                .setView(layout)
                .setPositiveButton(R.string.ok, null)
                .setCancelable(false);

        final AlertDialog mAlertDialog = dialog.create();
        mAlertDialog.setOnShowListener(new DialogInterface.OnShowListener(){

            @Override
            public void onShow(final DialogInterface dialog){
                Button button = mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        Log.d(TAG, "editText.getText().toString() : "+ editText_confirmNum.getText().toString());

                        String inputID = editText_confirmNum.getText().toString();
                        Log.d(TAG,"inputID : "+inputID);

                        String inputEmail = editText_Email.getText().toString();
                        Log.d(TAG,"inputEmail : "+inputEmail);

                        if(Utils.isConfirmNumInvalid(inputID)){
                            Toast.makeText(MainActivity.this,"Error, please try re-entering the number provided",Toast.LENGTH_SHORT).show();
                        }
                        else {

                            if(!Utils.isEmailValid(inputEmail)){
                                Toast.makeText(MainActivity.this,"Error, it is not an Email format",Toast.LENGTH_SHORT).show();
                            }else {

                                Log.d(TAG, "participant ID" + inputID);
                                Log.d(TAG, "group number" + inputID.substring(0, 1));

                                sharedPrefs.edit().putString("userid", inputID).apply();
                                Constants.USER_ID = sharedPrefs.getString("userid", "NA");
                                user_id.setText("Confirmation #");

                                sharedPrefs.edit().putString("groupNum", inputID.substring(0, 1)).apply();
                                Constants.GROUP_NUM = sharedPrefs.getString("groupNum", "NA");
                                num_6_digit.setText(Constants.USER_ID);

                                sharedPrefs.edit().putString("Email",inputEmail).apply();
                                Constants.Email = sharedPrefs.getString("Email", "NA");

                                startSettingSleepingTime(); //the appearing order is reversed from the code.

                                startpermission();

                                //TODO send a json to the server in the user collections.
                                sendingUserInform();

                                //TODO judging the time if the server storing the Study Start Time.
                                getStartDate();

                                startService(new Intent(getBaseContext(), SurveyTriggerService.class));


                                //Dismiss once everything is OK.
                                dialog.dismiss();
                            }
                        }
                        Log.d(TAG,"setPositiveButton");

                    }
                });

            }

        });

        mAlertDialog.show();

    }

    private void sendingUserInform(){

//       ex. http://mcog.asc.ohio-state.edu/apps/servicerec?deviceid=375996574474999&email=none@nobody.com&userid=3333333
//      deviceid=375996574474999&email=none@nobody.com&userid=3333333
        String link = Constants.checkInUrl + "deviceid=" + Constants.DEVICE_ID + "&email=" + Constants.Email+"&userid="+Constants.USER_ID;
        String userInformInString = null;
        JSONObject userInform = null;

        Log.d(TAG, "user inform link : "+ link);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                userInformInString = new HttpAsyncGetUserInformFromServer().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                        link).get();
            else
                userInformInString = new HttpAsyncGetUserInformFromServer().execute(
                        link).get();

            userInform = new JSONObject(userInformInString);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (JSONException e){
            e.printStackTrace();
        }

        Log.d(TAG, "userInform : " + userInform);

        //In order to set the survey link
        setDaysInSurvey(userInform);

        setMidnightStart(userInform);

    }

    private void setDaysInSurvey(JSONObject userInform){

        try{
            Constants.daysInSurvey = userInform.getInt("daysinsurvey");

            sharedPrefs.edit().putInt("daysInSurvey", Constants.daysInSurvey).apply();

            Log.d(TAG, "daysInSurvey : "+ Constants.daysInSurvey);
        }catch (JSONException e){
            e.printStackTrace();
        }

    }

    private void setMidnightStart(JSONObject userInform){

        try{
            //1519645071 firstcheckin
            long firstcheckin = userInform.getLong("firstcheckin") * Constants.MILLISECONDS_PER_SECOND;

            SimpleDateFormat sdf_now = new SimpleDateFormat(Constants.DATE_FORMAT_NOW_NO_ZONE);

            Log.d(TAG, "firstcheckin : "+ firstcheckin);
            Log.d(TAG, "firstcheckin String : "+ ScheduleAndSampleManager.getTimeString(firstcheckin, sdf_now));

            sdf_now = new SimpleDateFormat(Constants.DATE_FORMAT_NOW_DAY);

            long firstcheckinAfteraDay = firstcheckin + Constants.MILLISECONDS_PER_DAY;

            Log.d(TAG, "firstcheckinAfteraDay : "+ firstcheckinAfteraDay);

            String firstresearchdate = ScheduleAndSampleManager.getTimeString(firstcheckinAfteraDay, sdf_now);

            firstresearchdate = firstresearchdate + " 00:00:00";

            Log.d(TAG, "firstcheckinAfteraDay String : "+ firstresearchdate);

            long firstresearchday = ScheduleAndSampleManager.getTimeInMillis(firstresearchdate,sdf_now);

//            String firstcheckinDate = ScheduleAndSampleManager.getTimeString(firstcheckin, sdf_now);

            Constants.midnightstart = firstresearchday;

            sharedPrefs.edit().putLong("midnightstart", Constants.midnightstart).apply();

            Log.d(TAG, "midnightstart : "+ Constants.midnightstart);

            sharedPrefs.edit().putBoolean("resetIntervalSurveyFlag", true).apply();

        }catch (JSONException e){
            e.printStackTrace();
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(projName.equals("Ohio")) {
            switch (resultCode) {
                case 1:
                    String sleepStartTime = data.getExtras().getString("SleepingStartTime");
                    String sleepEndTime = data.getExtras().getString("SleepingEndTime");

                    if(!sleepStartTime.equals("Please select your start time")&&!sleepEndTime.equals("Please select your end time")) {

                        String[] sleepStartDetail = sleepStartTime.split(":");

                        int sleepStartHour = Integer.valueOf(sleepStartDetail[0]);
                        String sleepStartHourStr = "";
                        if(sleepStartHour<12)
                            sleepStartHourStr = String.valueOf(sleepStartHour)+"am";
                        else if(sleepStartHour==12)
                            sleepStartHourStr = String.valueOf(sleepStartHour)+"pm";
                        else
                            sleepStartHourStr = String.valueOf(sleepStartHour-12)+"pm";

                        String[] sleepEndDetail = sleepEndTime.split(":");

                        int sleepEndHour = Integer.valueOf(sleepEndDetail[0]);
                        String sleepEndHourStr = "";
                        if(sleepEndHour<12)
                            sleepEndHourStr = String.valueOf(sleepEndHour)+"am";
                        else if(sleepEndHour==12)
                            sleepEndHourStr = String.valueOf(sleepEndHour)+"pm";
                        else
                            sleepEndHourStr = String.valueOf(sleepEndHour-12)+"pm";
                        sleepingtime.setText("Sleep: " + sleepStartHourStr + " to " + sleepEndHourStr);

                        sleepingtime.setText("Sleep: " + sleepStartTime + " to " + sleepEndTime);


                        sharedPrefs.edit().putBoolean("resetIntervalSurveyFlag", true).apply();

                        /*
                        //cancel the last one first
                        unregisterActionAlarmReceiver();

                        //then, create the new one based on the new sleeping time
                        registerActionAlarmReceiver();
                        */
                    }
                    else
                        sleepingtime.setText("Set Sleep Time");

                    SharedPreferences.Editor editor_1 = getSharedPreferences("edu.umich.minuku_2", MODE_PRIVATE).edit();
                    editor_1.putString("SleepingStartTime",sleepStartTime);
                    editor_1.commit();
                    SharedPreferences.Editor editor_2 = getSharedPreferences("edu.umich.minuku_2", MODE_PRIVATE).edit();
                    editor_2.putString("SleepingEndTime",sleepEndTime);
                    editor_2.commit();

                    break;
            }
            /*switch (requestCode){
                case 2:
                    Log.d(TAG," case 2 ");
                    boolean tripstatusfromList = sharedPrefs.getBoolean("TripStatus", false);
                    boolean surveystatusfromList = sharedPrefs.getBoolean("SurveyStatus", false);

                    Log.d(TAG,"tripstatusfromList : " + tripstatusfromList + " , surveystatusfromList : " + surveystatusfromList);

                    Drawable drawable_red = getResources().getDrawable(R.drawable.circle_red);
                    drawable_red.setBounds(0, 0, (int)(drawable_red.getIntrinsicWidth()*0.5), (int)(drawable_red.getIntrinsicHeight()*0.5));
//            ScaleDrawable sd_red = new ScaleDrawable(drawable_red, 0, 30f, 30f);

                    Drawable drawable_green = getResources().getDrawable(R.drawable.circle_green);
                    drawable_green.setBounds(0, 0, (int)(drawable_green.getIntrinsicWidth()*0.5), (int)(drawable_green.getIntrinsicHeight()*0.5));
//            ScaleDrawable sd_green = new ScaleDrawable(drawable_green, 0, 30f, 30f);

                    if(tripstatusfromList)
                        ohio_annotate.setCompoundDrawables(null, null, drawable_red, null);
//                        tripStatus.setImageResource(R.drawable.circle_red);
                    else
                        ohio_annotate.setCompoundDrawables(null, null, drawable_green, null);
//                        tripStatus.setImageResource(R.drawable.circle_green);

                    if(surveystatusfromList)
                        tolinkList.setCompoundDrawables(null, null, drawable_red, null);
//                        surveyStatus.setImageResource(R.drawable.circle_red);
                    else
                        tolinkList.setCompoundDrawables(null, null, drawable_green, null);
//                        surveyStatus.setImageResource(R.drawable.circle_green);

                    break;
            }*/
        }
    }

    private long getSpecialTimeInMillis(String givenDateFormat){
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATE_FORMAT_NOW_SLASH);
        long timeInMilliseconds = 0;
        try {
            Date mDate = sdf.parse(givenDateFormat);
            timeInMilliseconds = mDate.getTime();
            Log.d(TAG,"Date in milli :: " + timeInMilliseconds);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return timeInMilliseconds;
    }

    private String addZero(int date){
        if(date<10)
            return String.valueOf("0"+date);
        else
            return String.valueOf(date);
    }

    public String makingDataFormat(int year,int month,int date){
        String dataformat= "";

//        dataformat = addZero(year)+"-"+addZero(month)+"-"+addZero(date)+" "+addZero(hour)+":"+addZero(min)+":00";
        dataformat = addZero(year)+"/"+addZero(month)+"/"+addZero(date)+" "+"00:00:00";
        Log.d(TAG,"dataformat : " + dataformat);

        return dataformat;
    }

    //to view sleepingohio
    private Button.OnClickListener ohio_annotateing = new Button.OnClickListener() {
        public void onClick(View v) {
            Log.e(TAG,"recordinglist_ohio clicked");

            startActivityForResult(new Intent(MainActivity.this, recordinglistohio.class), 2);

        }
    };

    //to view sleepingohio
    private Button.OnClickListener ohio_settinging = new Button.OnClickListener() {
        public void onClick(View v) {
            Log.e(TAG,"sleepingohio clicked");

            startActivityForResult(new Intent(MainActivity.this, sleepingohio.class),requestCode_setting);

        }
    };

    protected void showToast(String aText) {
        Toast.makeText(this, aText, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //menu.findItem(R.id.action_selectdate).setVisible(false);
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        if(projName.equals("Ohio")) //f***ing hardcode
            menu.findItem(R.id.action_report).setVisible(false);

        if(Constant.tabpos)
            menu.findItem(R.id.action_selectdate).setVisible(true);
        else
            menu.findItem(R.id.action_selectdate).setVisible(false);
        super.onPrepareOptionsMenu(menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_report:
                startActivity(new Intent(MainActivity.this, report.class));
                return true;
            case R.id.action_selectdate:
                final Calendar c = Calendar.getInstance();
                mYear = c.get(Calendar.YEAR);
                mMonth = c.get(Calendar.MONTH);
                mDay = c.get(Calendar.DAY_OF_MONTH);
                new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int day) {
                        String format = setDateFormat(year,month,day);
                        //startdate.setText(format);
                    }

                }, mYear,mMonth, mDay).show();
                return true;
        }
        return true;
    }

    private void checkAndRequestPermissions() {

        Log.e(TAG,"checkingAndRequestingPermissions");

        int permissionReadExternalStorage = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE);
        int permissionWriteExternalStorage = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE);

        int permissionFineLocation = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION);
        int permissionCoarseLocation = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION);
        int permissionStatus= ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE);

        List<String> listPermissionsNeeded = new ArrayList<>();


        if (permissionReadExternalStorage != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (permissionWriteExternalStorage != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (permissionFineLocation != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (permissionCoarseLocation != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(android.Manifest.permission.READ_PHONE_STATE);
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),REQUEST_ID_MULTIPLE_PERMISSIONS);
        }else{
            startServiceWork();
        }

    }


    public void getDeviceid(){

        TelephonyManager mngr = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
        int permissionStatus= ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE);
        if(permissionStatus==PackageManager.PERMISSION_GRANTED){
            Constants.DEVICE_ID = mngr.getDeviceId();

            Log.e(TAG,"DEVICE_ID"+Constants.DEVICE_ID+" : "+mngr.getDeviceId());

        }
    }

    public void startServiceWork(){

        getDeviceid();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_ID_MULTIPLE_PERMISSIONS: {

                Map<String, Integer> perms = new HashMap<>();

                // Initialize the map with both permissions
                perms.put(android.Manifest.permission.READ_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                perms.put(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                //perms.put(Manifest.permission.SYSTEM_ALERT_WINDOW, PackageManager.PERMISSION_GRANTED);
                perms.put(android.Manifest.permission.ACCESS_FINE_LOCATION, PackageManager.PERMISSION_GRANTED);
                perms.put(android.Manifest.permission.ACCESS_COARSE_LOCATION, PackageManager.PERMISSION_GRANTED);
                perms.put(android.Manifest.permission.READ_PHONE_STATE, PackageManager.PERMISSION_GRANTED);
                perms.put(android.Manifest.permission.BODY_SENSORS, PackageManager.PERMISSION_GRANTED);

                // Fill with actual results from user
                if (grantResults.length > 0) {
                    for (int i = 0; i < permissions.length; i++)
                        perms.put(permissions[i], grantResults[i]);
                    // Check for both permissions
                    if (perms.get(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                            && perms.get(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                            && perms.get(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            && perms.get(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            && perms.get(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                            && perms.get(android.Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED){
                        android.util.Log.d("permission", "[permission test]all permission granted");
                        //permission_ok=1;
                        startServiceWork();
                    } else {
                        Toast.makeText(this, "Go to settings and enable permissions", Toast.LENGTH_LONG).show();
                    }
                }


                //TODO after they permit all the permission, then start the work.
//                startService(new Intent(getBaseContext(), BackgroundService.class));
//                startTimerThread();

            }
        }
    }

    private String setDateFormat(int year,int monthOfYear,int dayOfMonth){
        return String.valueOf(year) + "/"
                + String.valueOf(monthOfYear + 1) + "/"
                + String.valueOf(dayOfMonth);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Subscribe
    public void incrementLoadingProcessCount(IncrementLoadingProcessCountEvent event) {
        Integer loadingCount = loadingProcessCount.incrementAndGet();
        Log.d(TAG, "Incrementing loading processes count: " + loadingCount);
    }

    @Subscribe
    public void decrementLoadingProcessCountEvent(DecrementLoadingProcessCountEvent event) {
        Integer loadingCount = loadingProcessCount.decrementAndGet();
        Log.d(TAG, "Decrementing loading processes count: " + loadingCount);
    }

    private class HttpAsyncGetUserInformFromServer extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {

            String result=null;
//            String url = params[0];

            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                URL url = new URL(params[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();


                InputStream stream = connection.getInputStream();

                reader = new BufferedReader(new InputStreamReader(stream));

                StringBuffer buffer = new StringBuffer();
                String line = "";

                while ((line = reader.readLine()) != null) {
                    buffer.append(line+"\n");
                    Log.d(TAG, "Response : " + line);
                }

                return buffer.toString();

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return result;
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG, "get http post result " + result);

        }

    }


}
