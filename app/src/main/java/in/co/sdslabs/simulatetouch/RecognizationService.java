package in.co.sdslabs.simulatetouch;

import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.List;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.PocketSphinx;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.RecognitionListener;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

/**
 * Created by arpit on 17/10/15.
 */
public class RecognizationService extends Service implements RecognitionListener {

    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wakeup";
    private static final String MENU_SEARCH = "menu";
    private static final List<String> WORDS_LIST = Arrays.asList("up", "down", "left", "right", "stop");
    private static final String KEYPHRASE = "ok google";

    private SpeechRecognizer recognizer;
    private Process process;
    private DataOutputStream os;
    private String cmd;
    private Toast toast;

    @Override
    public void onCreate() {
        Log.d("In service: ", "service started");


        try {
            process = Runtime.getRuntime().exec("su");
        } catch (IOException e) {
            e.printStackTrace();
        }

        os = new DataOutputStream(process.getOutputStream());

        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(RecognizationService.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                }
                catch(IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception exception) {
                if(exception!=null) {
                    Log.d("In service: ", "Failed to initiate Recognizer");
                }
                else {
                    sendBroadcast(new Intent("android.intent.action.MAIN"));
                    recognizer.stop();
                    recognizer.startListening(KWS_SEARCH);
                }
            }
        }.execute();


    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

                        // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .setRawLogDir(assetsDir)

                        // Threshold to tune for keyphrase to balance between false alarms and misses
                .setKeywordThreshold(1e-45f)

                        // Use context-independent phonetic search, context-dependent is too slow for mobile
                .setBoolean("-allphone_ci", true)

                .getRecognizer();
        recognizer.addListener(this);

        /** In your application you might not need to add all those searches.
         * They are added here for demonstration. You can leave just one.
         */

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

        // Create grammar-based search for selection between demos
        File menuGrammar = new File(assetsDir, "menu.gram");
        recognizer.addGrammarSearch(MENU_SEARCH, menuGrammar);

    }

    @Override
    public void onBeginningOfSpeech() {
        Log.d("In service: ", "beginning of speech");
    }

    @Override
    public void onEndOfSpeech() {
        Log.d("In service: ", "end of speech");
    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {

        if(hypothesis == null) {
            return;
        }

        String text = hypothesis.getHypstr();
        Log.d("In service: ", "Partial result got: " + text);

        if(WORDS_LIST.contains(text)){
            Log.d("In service: ", text);
//            Magic happens here!
            toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
            toast.show();
            switch (text) {
                case "right":
                    cmd = "/system/bin/input swipe 50 400 850 400\n";
                    try {
                        os.writeBytes(cmd);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "left":
                    cmd = "/system/bin/input swipe 430 300 -370 300\n";
                    try {
                        os.writeBytes(cmd);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "up":
                    cmd = "/system/bin/input swipe 300 650 300 -400\n";
                    try {
                        os.writeBytes(cmd);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "down":
                    cmd = "/system/bin/input swipe 200 100 200 1150\n";
                    try {
                        os.writeBytes(cmd);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "stop":
                    recognizer.stop();
                    recognizer.startListening(KWS_SEARCH);
                    break;
                default:
                    Log.d("In service: ", "This can't happen!!!!!");
                    break;
            }

            recognizer.stop();
            recognizer.startListening(MENU_SEARCH);

        }
        else if(text.equals(KEYPHRASE)){
            toast = Toast.makeText(getApplicationContext(), "Listening...", Toast.LENGTH_LONG);
            toast.show();
            recognizer.stop();
            recognizer.startListening(MENU_SEARCH);
        }
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                toast.cancel();
            }
        }, 500);

    }

    @Override
    public void onDestroy() {

        toast = Toast.makeText(getApplicationContext(), "Closing service...", Toast.LENGTH_LONG);
        toast.show();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                toast.cancel();
            }
        }, 500);
        Log.d("In session: ", "service destroyed");
        super.onDestroy();
    }

    @Override
    public void onResult(Hypothesis hypothesis) {

        if(hypothesis != null) {
            String text = hypothesis.getHypstr();
            Log.d("In service: ", "Result got: " + text);
        }
    }

    @Override
    public void onError(Exception e) {
        Log.d("In service: ", e.getMessage());
    }

    @Override
    public void onTimeout() {
        Log.d("In service: ", "on timeout");
    }
}
