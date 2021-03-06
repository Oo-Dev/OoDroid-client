package org.oo.oodroid_client;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.os.Handler;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.CharBuffer;


public class MainActivity extends Activity implements View.OnClickListener {

    private final static String TAG = "MainActivity";
    private final static String KEY_SERVER_HOST = "server_host";
    private final static String KEY_SERVER_PORT = "server_port";

    public final static String REQUEST_SDP = "REQUEST SDP FILE";

    private final static String SDP_FILE_PATH = "session.sdp";

    /** Constants below are used to update UI with handler*/
    private final static int MSG_CONNECTING = 0;
    private final static int MSG_STORING = 1;
    private final static int MSG_SENDING_REQUEST = 2;
    private final static int MSG_DONE = 3;
    
    private final static int MSG_ERROR = 4;
    private final static int MSG_CONNECT_ERROR = 5;
    private final static int MSG_STORE_ERROR = 6;
    private final static int MSG_DOWNLOAD_ERROR = 7;

    
    
    //
    
    private final static String DEFAULT_HOST = "192.168.43.1";
    private final static int DEFAULT_PORT = 25581;
    
    private int port = DEFAULT_PORT;
    private String host = DEFAULT_HOST;
    
    private Button mConnectButton;
    private ProgressBar mWaitBar;
    private TextView mStatus;
    //private EditText
    private SharedPreferences mSettings;
    private SharedPreferences.Editor editor;
    Handler mHandler;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mConnectButton = (Button)findViewById(R.id.bt_connect);
        mWaitBar = (ProgressBar)findViewById(R.id.pb_wait);
        mStatus = (TextView)findViewById(R.id.tv_status);
        
        mSettings = this.getPreferences(MODE_PRIVATE);
        editor = mSettings.edit();
                
        mConnectButton.setOnClickListener(this);
        mHandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.arg1){
                    case MSG_CONNECTING:mStatus.setText(R.string.status_connect);break;
                    case MSG_STORING:mStatus.setText(R.string.status_cache);break;
                    case MSG_DONE:mStatus.setText(R.string.status_done);initUI(true);break;
                    case MSG_SENDING_REQUEST:mStatus.setText(R.string.status_request);break;
                    case MSG_CONNECT_ERROR:logError(getApplicationContext().getResources().getText(R.string.error_connect).toString());
                        initUI(false);break;
                    case MSG_STORE_ERROR:logError(getApplicationContext().getResources().getText(R.string.error_cache).toString());
                        initUI(false);break;
                    case MSG_DOWNLOAD_ERROR:logError(getApplicationContext().getResources().getText(R.string.error_download).toString());
                        initUI(false);break;
                    default:break;
                }
                    
            }
        };
    }
    
    private void initUI(boolean isDone){
        mConnectButton.setClickable(true);
        mWaitBar.setVisibility(View.INVISIBLE);
        if(!isDone)
            mStatus.setText(R.string.demo);
        else
            mStatus.setText(R.string.status_done);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /** Displays a popup to report the error to the user */
    private void logError(final String msg) {
        final String error = (msg == null) ? "Error unknown" : msg;
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(error).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {}
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    
    @Override
    public void onClick(View v) {
        mConnectButton.setClickable(false);
        mWaitBar.setVisibility(View.VISIBLE);
        new WorkerThread(host,port).start();

    }
    
    
    
    class WorkerThread extends Thread{

        Socket mSocket;
        String host;
        int port;
        Message msg;
        String mSessionDescription = null;
        FileOutputStream fileWriter;
        
        WorkerThread(String host,int port){
            this.host = host;
            this.port = port;
            
        }
        
        @Override
        public void run() {
            Log.d(TAG, "Start to connect");
            updateUI(MSG_CONNECTING);
            try {
                Log.d(TAG,"Sending request to " + host + ":" + port);
                mSocket = new Socket(host, port);
                Log.d(TAG,"Connect OK");
                updateUI(MSG_SENDING_REQUEST);
                PrintWriter pw = new PrintWriter(mSocket.getOutputStream());
                pw.println(REQUEST_SDP);
                pw.flush();
            } catch (IOException e) {
                updateUI(MSG_CONNECT_ERROR);
                Log.e(TAG,"connect error");
                e.printStackTrace();
                return ;
            }
            Log.i(TAG, "connect success");
            
           
            updateUI(MSG_STORING);
            Log.d(TAG,"Downloading sdp file");
            try {
                BufferedReader bf =  new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
                String line ;
                while((line = bf.readLine()) != null){
                    line += '\n';
                    if(mSessionDescription != null)
                        mSessionDescription += line;
                    else
                        mSessionDescription = line;
                }
                Log.d(TAG,"Got session description \n" + mSessionDescription);
                //TODO Handle error request
            } catch (IOException e) {
                updateUI(MSG_DOWNLOAD_ERROR);
                e.printStackTrace();
                return ;
            }
            
            Log.d(TAG,"Storing sdp file");
            try {
                //TODO create file when unexisted
                    fileWriter = getApplicationContext().openFileOutput(SDP_FILE_PATH, MODE_WORLD_READABLE);
                    
                //}
            }  catch (IOException e) {
                e.printStackTrace();
                updateUI(MSG_STORE_ERROR);
                return ;
            }
            try {
                if(mSessionDescription != null)
                    fileWriter.write(mSessionDescription.getBytes());
                fileWriter.flush();
                fileWriter.close();
                
                //debug output content of file
                InputStream is = getApplicationContext().openFileInput(SDP_FILE_PATH);
                ByteArrayOutputStream ba = new ByteArrayOutputStream();
                byte[] buff = new byte[1024];
                int length = 0;
                while((length = is.read(buff)) != -1){
                    ba.write(buff,0,length);
                }
                Log.d(TAG,"string in file is :\n" + ba.toString());
                ba.close();
                is.close();
                
            } catch (IOException e) {
                updateUI(MSG_STORE_ERROR);
                e.printStackTrace();
            }
 
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
                updateUI(MSG_CONNECT_ERROR);
            }


            updateUI(MSG_DONE);
            Log.i(TAG, "store sdp file success");
            
            Log.d(TAG, "Opening sdp file from " + getApplicationContext().getFilesDir());
            startActivity(getVideoFileIntent(new File(getApplicationContext().getFilesDir() + "/" + SDP_FILE_PATH)));
        }
        
        private void updateUI(int arg){
            msg = mHandler.obtainMessage();
            msg.arg1 = arg;
            mHandler.sendMessage(msg);
            //msg.recycle();
        }

        private Intent getVideoFileIntent(File file)
        {
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("oneshot", 0);
            intent.putExtra("configchange", 0);
            Uri uri = Uri.fromFile(file);
            intent.setDataAndType(uri, "video/*");
            return intent;
        }
    }
}
