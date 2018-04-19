package com.regula.documentreader;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.regula.documentreader.api.DocumentReader;
import com.regula.documentreader.api.enums.DocReaderAction;
import com.regula.documentreader.api.enums.eGraphicFieldType;
import com.regula.documentreader.api.enums.eRFID_Password_Type;
import com.regula.documentreader.api.enums.eVisualFieldType;
import com.regula.documentreader.api.params.RfidScenario;
import com.regula.documentreader.api.results.DocumentReaderResults;
import com.regula.documentreader.api.results.DocumentReaderScenario;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static android.graphics.BitmapFactory.decodeStream;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_BROWSE_PICTURE = 11;
    private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 22;
    private static final String MY_SHARED_PREFS = "MySharedPrefs";
    private static final String DO_RFID = "doRfid";

    private TextView nameTv;
    private TextView showScanner;
    private TextView recognizeImage;

    private ImageView portraitIv;
    private ImageView docImageIv;

    private CheckBox doRfidCb;

    private ListView scenarioLv;

    private SharedPreferences sharedPreferences;
    private boolean doRfid;
    private AlertDialog loadingDialog;

    private int selectedPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nameTv = findViewById(R.id.nameTv);
        showScanner = findViewById(R.id.showScannerLink);
        recognizeImage = findViewById(R.id.recognizeImageLink);

        portraitIv = findViewById(R.id.portraitIv);
        docImageIv = findViewById(R.id.documentImageIv);

        scenarioLv = findViewById(R.id.scenariosList);

        doRfidCb = findViewById(R.id.doRfidCb);

        sharedPreferences = getSharedPreferences(MY_SHARED_PREFS, MODE_PRIVATE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        final AlertDialog initDialog = showDialog("Initializing");

        //Reading the license from raw resource file
        try {
            InputStream licInput = getResources().openRawResource(R.raw.regula);
            int available = licInput.available();
            byte[] license = new byte[available];
            //noinspection ResultOfMethodCallIgnored
            licInput.read(license);

            //Initializing the reader
            DocumentReader.Instance().initializeReader(MainActivity.this, license, new DocumentReader.DocumentReaderInitCompletion() {
                @Override
                public void onInitCompleted(boolean success, String error) {
                    if(initDialog.isShowing()) {
                        initDialog.dismiss();
                    }

                    DocumentReader.Instance().showHintMessages = true;
                    DocumentReader.Instance().videoCaptureMotionControl = true;

                    //initialization successful
                    if(success){
                        showScanner.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                clearResults();

                                //starting video processing
                                DocumentReader.Instance().showScanner(completion);
                            }
                        });

                        recognizeImage.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                clearResults();
                                //checking for image browsing permissions
                                if (ContextCompat.checkSelfPermission(MainActivity.this,
                                        Manifest.permission.READ_EXTERNAL_STORAGE)
                                        != PackageManager.PERMISSION_GRANTED) {

                                    ActivityCompat.requestPermissions(MainActivity.this,
                                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                            PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                                } else {
                                    //start image browsing
                                    createImageBrowsingRequest();
                                }
                            }
                        });

                        //reading shared preferences
                        doRfid = sharedPreferences.getBoolean(DO_RFID, false);
                        doRfidCb.setChecked(doRfid);
                        doRfidCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                                doRfid = checked;
                                sharedPreferences.edit().putBoolean(DO_RFID, checked).apply();
                            }
                        });

                        //getting current processing scenario and loading available scenarios to ListView
                        String currentScenario = DocumentReader.Instance().processParams.scenario;
                        ArrayList<String> scenarios = new ArrayList<>();
                        for(DocumentReaderScenario scenario: DocumentReader.Instance().availableScenarios){
                            scenarios.add(scenario.name);
                        }

                        //setting default scenario
                        if(currentScenario==null || currentScenario.isEmpty()){
                            currentScenario = scenarios.get(0);
                        }

                        final ScenarioAdapter adapter = new ScenarioAdapter(MainActivity.this, android.R.layout.simple_list_item_1, scenarios);
                        selectedPosition = 0;
                        try{
                            selectedPosition = adapter.getPosition(currentScenario);
                        } catch (Exception ex){
                            ex.printStackTrace();
                        }
                        scenarioLv.setAdapter(adapter);

                        scenarioLv.setSelection(selectedPosition);

                        scenarioLv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                                //setting selected scenario to DocumentReader params
                                DocumentReader.Instance().processParams.scenario = adapter.getItem(i);
                                selectedPosition = i;
                                adapter.notifyDataSetChanged();

                            }
                        });

                    }
                    //Initialization was not successful
                    else {
                        Toast.makeText(MainActivity.this, "Init failed:" + error, Toast.LENGTH_LONG).show();
                    }
                }
            });

            licInput.close();

        } catch (Exception ex){
            ex.printStackTrace();
        }
    }



    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            //Image browsing intent processed successfully
            if (requestCode == REQUEST_BROWSE_PICTURE){
                if (data.getData() != null) {
                    Uri selectedImage = data.getData();
                    Bitmap bmp = getBitmap(selectedImage, 1920, 1080);

                    loadingDialog = showDialog("Processing image");

                    DocumentReader.Instance().recognizeImage(bmp, completion);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //access to gallery is allowed
                    createImageBrowsingRequest();
                } else {
                    Toast.makeText(MainActivity.this, "Permission required, to browse images",Toast.LENGTH_LONG).show();
                }
            } break;
        }
    }

    //DocumentReader processing callback
    private DocumentReader.DocumentReaderCompletion completion = new DocumentReader.DocumentReaderCompletion() {
        @Override
        public void onCompleted(int action, DocumentReaderResults results, String error) {
            //processing is finished, all results are ready
            if (action == DocReaderAction.COMPLETE) {
                if(loadingDialog!=null && loadingDialog.isShowing()){
                    loadingDialog.dismiss();
                }

                //Checking, if nfc chip reading should be performed
                if (doRfid && results!=null && results.chipPage != 0) {
                    if (DocumentReader.Instance().processParams.rfidScenario == null) {
                        DocumentReader.Instance().processParams.rfidScenario = new RfidScenario();
                    }

                    //setting the chip's access key - mrz on car access number
                    String accessKey = null;
                    if ((accessKey = results.getTextFieldValueByType(eVisualFieldType.FT_MRZ_STRINGS)) != null && !accessKey.isEmpty()) {
                        accessKey = results.getTextFieldValueByType(eVisualFieldType.FT_MRZ_STRINGS)
                                .replace("^", "");
                        DocumentReader.Instance().processParams.rfidScenario.mrz = accessKey;
                        DocumentReader.Instance().processParams.rfidScenario.pacePasswordType = eRFID_Password_Type.PPT_MRZ;
                    } else if ((accessKey = results.getTextFieldValueByType(eVisualFieldType.FT_CARD_ACCESS_NUMBER)) != null && !accessKey.isEmpty()) {
                        DocumentReader.Instance().processParams.rfidScenario.password = accessKey;
                        DocumentReader.Instance().processParams.rfidScenario.pacePasswordType = eRFID_Password_Type.PPT_CAN;
                    }

                    //starting chip reading
                    DocumentReader.Instance().startRFIDReader(new DocumentReader.DocumentReaderCompletion() {
                        @Override
                        public void onCompleted(int rfidAction, DocumentReaderResults results, String error) {
                            if (rfidAction == DocReaderAction.COMPLETE) {
                                displayResults(results);
                            }
                        }
                    });
                } else {
                    displayResults(results);
                }
            } else {
                //something happened before all results were ready
                if(action==DocReaderAction.CANCEL){
                    Toast.makeText(MainActivity.this, "Scanning was cancelled",Toast.LENGTH_LONG).show();
                } else if(action == DocReaderAction.ERROR){
                    Toast.makeText(MainActivity.this, "Error:" + error, Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    private AlertDialog showDialog(String msg) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
        View dialogView = getLayoutInflater().inflate(R.layout.simple_dialog, null);
        dialog.setTitle(msg);
        dialog.setView(dialogView);
        dialog.setCancelable(false);
        return dialog.show();
    }

    //show received results on the UI
    private void displayResults(DocumentReaderResults results){
        if(results!=null) {
            String name = results.getTextFieldValueByType(eVisualFieldType.FT_SURNAME_AND_GIVEN_NAMES);
            if (name != null){
                nameTv.setText(name);
            }

            Bitmap portrait = results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT);
            if(portrait!=null){
                portraitIv.setImageBitmap(portrait);
            }

            Bitmap documentImage = results.getGraphicFieldImageByType(eGraphicFieldType.GT_DOCUMENT_FRONT);
            if(documentImage!=null){
                docImageIv.setImageBitmap(documentImage);
            }
        }
    }

    private void clearResults(){
        nameTv.setText("");
        portraitIv.setImageResource(R.drawable.portrait);
        docImageIv.setImageResource(R.drawable.id);
    }

    // creates and starts image browsing intent
    // results will be handled in onActivityResult method
    private void createImageBrowsingRequest() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_BROWSE_PICTURE);
    }

    // loads bitmap from uri
    private Bitmap getBitmap(Uri selectedImage, int targetWidth, int targetHeight) {
        ContentResolver resolver = MainActivity.this.getContentResolver();
        InputStream is = null;
        try {
            is = resolver.openInputStream(selectedImage);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, options);

        //Re-reading the input stream to move it's pointer to start
        try {
            is = resolver.openInputStream(selectedImage);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return decodeStream(is, null, options);
    }

    // see https://developer.android.com/topic/performance/graphics/load-bitmap.html
    private int calculateInSampleSize(BitmapFactory.Options options, int bitmapWidth, int bitmapHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > bitmapHeight || width > bitmapWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > bitmapHeight
                    && (halfWidth / inSampleSize) > bitmapWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    class ScenarioAdapter extends ArrayAdapter<String>{

        public ScenarioAdapter(@NonNull Context context, int resource, @NonNull List<String> objects) {
            super(context, resource, objects);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            if(position == selectedPosition){
                view.setBackgroundColor(Color.LTGRAY);
            } else {
                view.setBackgroundColor(Color.TRANSPARENT);
            }
            return view;
        }
    }
}
