package in.co.sdslabs.simulatetouch;

import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

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
    private static final List<String> WORDS_LIST = Arrays.asList("up", "down", "left", "right");
    private static final String KEYPHRASE = "ok google";


    private SpeechRecognizer recognizer;
    private Process process;
    private DataOutputStream os;
    private String cmd;
    private WindowManager windowManager;
    private ImageView floatingHead;
    private WindowManager.LayoutParams layoutParams;
    private Display display;
    private Point screenSize = new Point(0,0);

    @Override
    public void onCreate() {
        Log.d("In service: ", "service started");


        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        display = windowManager.getDefaultDisplay();
        display.getSize(screenSize);

        floatingHead = new ImageView(this);
        floatingHead.setBackgroundResource(R.mipmap.floating_head);

        layoutParams = new WindowManager.LayoutParams(
                (int)Math.sqrt(screenSize.x*screenSize.y/100*Math.PI), (int)Math.sqrt(screenSize.x*screenSize.y/100*Math.PI),
                WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 0;
        layoutParams.y = 100;

        try {
            process = Runtime.getRuntime().exec("su");
        } catch (IOException e) {
            e.printStackTrace();
        }

        floatingHead.setOnTouchListener(new View.OnTouchListener() {
//            private int initialX;
//            private int initialY;
//            private float initialTouchX;
//            private float initialTouchY;
//
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                stopSelf();
//                switch (event.getAction()) {
//                    case MotionEvent.ACTION_DOWN:
//                        initialX = layoutParams.x;
//                        initialY = layoutParams.y;
//                        initialTouchX = event.getRawX();
//                        initialTouchY = event.getRawY();
//                        return true;
//                    case MotionEvent.ACTION_UP:
//                        return true;
//                    case MotionEvent.ACTION_MOVE:
//                        layoutParams.x = initialX
//                                + (int) (event.getRawX() - initialTouchX);
//                        layoutParams.y = initialY
//                                + (int) (event.getRawY() - initialTouchY);
//                        windowManager.updateViewLayout(floatingHead, layoutParams);
//                        return true;
//                }
                return true;
            }
        });

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
                    recognizer.stop();
                    recognizer.startListening(KWS_SEARCH);
                    windowManager.addView(floatingHead, layoutParams);
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
            Log.d("In service: ",text);
//            Magic happens here!
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
                default:
                    Log.d("In service: ", "This can't happen!!!!!");
                    break;
            }

            recognizer.stop();
            recognizer.startListening(MENU_SEARCH);

        }
        else if(text.equals(KEYPHRASE)){
            recognizer.stop();
            recognizer.startListening(MENU_SEARCH);
        }

    }

    @Override
    public void onDestroy() {
        Log.d("In session: ", "service destroyed");
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
