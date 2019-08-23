package com.ru.cordova.printer.bluetooth;

import java.io.UnsupportedEncodingException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Set;
import java.util.UUID;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Bitmap.Config;
import android.util.Xml.Encoding;
import android.util.Base64;
import java.util.ArrayList;
import java.util.List;


import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;
import android.util.Log;
import com.bxl.BXLConst;
import com.bxl.config.editor.BXLConfigLoader;
import java.io.IOException;
import java.nio.ByteBuffer;
import jpos.JposConst;
import jpos.JposException;
import jpos.POSPrinter;
import jpos.POSPrinterConst;
import jpos.config.JposEntry;
import jpos.events.ErrorEvent;
import jpos.events.ErrorListener;
import jpos.events.OutputCompleteEvent;
import jpos.events.OutputCompleteListener;
import jpos.events.StatusUpdateEvent;
import jpos.events.StatusUpdateListener;


public class BluetoothPrinter extends CordovaPlugin {

    private static final String LOG_TAG = "BluetoothPrinter";
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;
    Bitmap bitmap;

    private BXLConfigLoader bxlConfigLoader;
    private POSPrinter posPrinter;
    private String logicalName;
    //Context context;

    private int brightness = 50;
    private int compress = 1;
    

    public BluetoothPrinter() {
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("list")) {
            String name = args.getString(0);
            listBT(callbackContext, name);
            return true;
        } else if (action.equals("connect")) {
            String name = args.getString(0);
            if (findBT(callbackContext, name)) {
                try {
                    connectBT(callbackContext);
                } catch (IOException e) {
                    Log.e(LOG_TAG, e.getMessage());
                    e.printStackTrace();
                }
            } else {
                callbackContext.error("Bluetooth Device Not Found: " + name);
            }
            return true;
        } else if (action.equals("disconnect")) {
            try {
                disconnectBT(callbackContext);
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        } else if (action.equals("print") || action.equals("printImage")) {
            try {
                String msg = args.getString(0);
                printImage(callbackContext, msg);
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        } else if (action.equals("printBase64")) {
            try {
                String msg = args.getString(0);
                printBase64(callbackContext, msg);
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        } else if (action.equals("printText")) {
            try {
                String msg = args.getString(0);
                printText(callbackContext, msg);
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        } else if (action.equals("printPOSCommand")) {
            try {
                String msg = args.getString(0);
                printPOSCommand(callbackContext, hexStringToBytes(msg));
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    //This will return the array list of paired bluetooth printers
    void listBT(CallbackContext callbackContext,String name) {
        Context context = this.cordova.getActivity().getApplicationContext();
        if (start(context)) {
          try {
            posPrinter.open(logicalName);
            posPrinter.claim(0);
            posPrinter.setDeviceEnabled(true);
    
            String ESC = new String(new byte[] { 0x1b, 0x7c });
            String LF = "\n";
    
            posPrinter.setCharacterEncoding(BXLConst.CS_858_EURO);
            posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, "holamundo" + "\n");
    
          } catch (JposException e) {
            e.printStackTrace();
          } finally {
            try {
              posPrinter.close();
            } catch (JposException e) {
              e.printStackTrace();
            }
          }
        }
    }

    // This will find a bluetooth printer device
    boolean findBT(CallbackContext callbackContext, String name) {
        try {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null) {
                Log.e(LOG_TAG, "No bluetooth adapter available");
            }
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                this.cordova.getActivity().startActivityForResult(enableBluetooth, 0);
            }
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().equalsIgnoreCase(name)) {
                        mmDevice = device;
                        return true;
                    }
                }
            }
            Log.d(LOG_TAG, "Bluetooth Device Found: " + mmDevice.getName());
        } catch (Exception e) {
            String errMsg = e.getMessage();
            Log.e(LOG_TAG, errMsg);
            e.printStackTrace();
            callbackContext.error(errMsg);
        }
        return false;
    }

    // Tries to open a connection to the bluetooth printer device
    boolean connectBT(CallbackContext callbackContext) throws IOException {
        try {
            // Standard SerialPortService ID
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            mmSocket.connect();
            mmOutputStream = mmSocket.getOutputStream();
            mmInputStream = mmSocket.getInputStream();
            beginListenForData();
            //Log.d(LOG_TAG, "Bluetooth Opened: " + mmDevice.getName());
            callbackContext.success("Bluetooth Opened: " + mmDevice.getName());
            return true;
        } catch (Exception e) {
            String errMsg = e.getMessage();
            Log.e(LOG_TAG, errMsg);
            e.printStackTrace();
            callbackContext.error(errMsg);
        }
        return false;
    }

    // After opening a connection to bluetooth printer device,
    // we have to listen and check if a data were sent to be printed.
    void beginListenForData() {
        try {
            final Handler handler = new Handler();
            // This is the ASCII code for a newline character
            final byte delimiter = 10;
            stopWorker = false;
            readBufferPosition = 0;
            readBuffer = new byte[1024];
            workerThread = new Thread(new Runnable() {
                public void run() {
                    while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                        try {
                            int bytesAvailable = mmInputStream.available();
                            if (bytesAvailable > 0) {
                                byte[] packetBytes = new byte[bytesAvailable];
                                mmInputStream.read(packetBytes);
                                for (int i = 0; i < bytesAvailable; i++) {
                                    byte b = packetBytes[i];
                                    if (b == delimiter) {
                                        byte[] encodedBytes = new byte[readBufferPosition];
                                        System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                        /*
                                         final String data = new String(encodedBytes, "US-ASCII");
                                         readBufferPosition = 0;
                                         handler.post(new Runnable() {
                                         public void run() {
                                         myLabel.setText(data);
                                         }
                                         });
                                         */
                                    } else {
                                        readBuffer[readBufferPosition++] = b;
                                    }
                                }
                            }
                        } catch (IOException ex) {
                            stopWorker = true;
                        }
                    }
                }
            });
            workerThread.start();
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //This will send data to bluetooth printer
    boolean printText(CallbackContext callbackContext, String msg) throws IOException {
        try {
            mmOutputStream.write(msg.getBytes());
            // tell the user data were sent
            //Log.d(LOG_TAG, "Data Sent");
            callbackContext.success("Data Sent");
            return true;

        } catch (Exception e) {
            String errMsg = e.getMessage();
            Log.e(LOG_TAG, errMsg);
            e.printStackTrace();
            callbackContext.error(errMsg);
        }
        return false;
    }

    //This will send data to bluetooth printer
    boolean printImage(CallbackContext callbackContext, String msg) throws IOException {
        try {

            final String encodedString = msg;
            final String pureBase64Encoded = encodedString.substring(encodedString.indexOf(",") + 1);
            final byte[] decodedBytes = Base64.decode(pureBase64Encoded, Base64.DEFAULT);

            Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            bitmap = decodedBitmap;
            int mWidth = bitmap.getWidth();
            int mHeight = bitmap.getHeight();

            bitmap = resizeImage(bitmap, 48 * 8, mHeight);

            byte[] bt = decodeBitmap(bitmap);

            mmOutputStream.write(bt);
            // tell the user data were sent
            //Log.d(LOG_TAG, "Data Sent");
            callbackContext.success("Data Sent");
            return true;

        } catch (Exception e) {
            String errMsg = e.getMessage();
            Log.e(LOG_TAG, errMsg);
            e.printStackTrace();
            callbackContext.error(errMsg);
        }
        return false;
    }

    boolean printPOSCommand(CallbackContext callbackContext, byte[] buffer) throws IOException {
        try {
            mmOutputStream.write(buffer);
            // tell the user data were sent
            Log.d(LOG_TAG, "Data Sent");
            callbackContext.success("Data Sent");
            return true;
        } catch (Exception e) {
            String errMsg = e.getMessage();
            Log.e(LOG_TAG, errMsg);
            e.printStackTrace();
            callbackContext.error(errMsg);
        }
        return false;
    }

    //NEW BASE64 IMAGE
    boolean printBase64(CallbackContext callbackContext, String msg) throws IOException {
        try {

            final String encodedString = msg;
            final String pureBase64Encoded = encodedString.substring(encodedString.indexOf(",") + 1);
            final byte[] decodedBytes = Base64.decode(pureBase64Encoded, Base64.DEFAULT);

            Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            bitmap = decodedBitmap;
            int mWidth = bitmap.getWidth();
            int mHeight = bitmap.getHeight();

            bitmap = resizeImage(bitmap, 48 * 8, mHeight);

            byte[] bt = decodeBitmapBase64(bitmap);

            mmOutputStream.write(bt);
            // tell the user data were sent
            Log.d(LOG_TAG, "PRINT BASE64 SEND");
            callbackContext.success("PRINT BASE64 SEND");
            return true;

        } catch (Exception e) {
            String errMsg = e.getMessage();
            Log.e(LOG_TAG, errMsg);
            e.printStackTrace();
            callbackContext.error(errMsg);
        }
        return false;
    }
    //NEW DECODE IMAGE BASE64
    public static byte[] decodeBitmapBase64(Bitmap bmp) {
        int bmpWidth = bmp.getWidth();
        int bmpHeight = bmp.getHeight();
        List<String> list = new ArrayList<String>(); //binaryString list
        StringBuffer sb;
        int bitLen = bmpWidth / 8;
        int zeroCount = bmpWidth % 8;
        String zeroStr = "";
        if (zeroCount > 0) {
            bitLen = bmpWidth / 8 + 1;
            for (int i = 0; i < (8 - zeroCount); i++) {
                zeroStr = zeroStr + "0";
            }
        }

        for (int i = 0; i < bmpHeight; i++) {
            sb = new StringBuffer();
            for (int j = 0; j < bmpWidth; j++) {
                int color = bmp.getPixel(j, i);

                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                // if color close to white，bit='0', else bit='1'
                if (r > 160 && g > 160 && b > 160) {
                    sb.append("0");
                } else {
                    sb.append("1");
                }
            }
            if (zeroCount > 0) {
                sb.append(zeroStr);
            }
            list.add(sb.toString());
        }

        List<String> bmpHexList = binaryListToHexStringList(list);
        String commandHexString = "1D763000";

        //construct xL and xH
        //there are 8 pixels per byte. In case of modulo: add 1 to compensate.
        bmpWidth = bmpWidth % 8 == 0 ? bmpWidth / 8 : (bmpWidth / 8 + 1);
        int xL = bmpWidth % 256;
        int xH = (bmpWidth - xL) / 256;

        String xLHex = Integer.toHexString(xL);
        String xHHex = Integer.toHexString(xH);
        if (xLHex.length() == 1) {
            xLHex = "0" + xLHex;
        }
        if (xHHex.length() == 1) {
            xHHex = "0" + xHHex;
        }
        String widthHexString = xLHex + xHHex;

        //construct yL and yH
        int yL = bmpHeight % 256;
        int yH = (bmpHeight - yL) / 256;

        String yLHex = Integer.toHexString(yL);
        String yHHex = Integer.toHexString(yH);
        if (yLHex.length() == 1) {
            yLHex = "0" + yLHex;
        }
        if (yHHex.length() == 1) {
            yHHex = "0" + yHHex;
        }
        String heightHexString = yLHex + yHHex;

        List<String> commandList = new ArrayList<String>();
        commandList.add(commandHexString + widthHexString + heightHexString);
        commandList.addAll(bmpHexList);

        return hexList2Byte(commandList);
    }
    
    // disconnect bluetooth printer.
    boolean disconnectBT(CallbackContext callbackContext) throws IOException {
        try {
            stopWorker = true;
            mmOutputStream.close();
            mmInputStream.close();
            mmSocket.close();
            callbackContext.success("Bluetooth Disconnect");
            return true;
        } catch (Exception e) {
            String errMsg = e.getMessage();
            Log.e(LOG_TAG, errMsg);
            e.printStackTrace();
            callbackContext.error(errMsg);
        }
        return false;
    }

    //New implementation, change old
    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    //New implementation
    private static Bitmap resizeImage(Bitmap bitmap, int w, int h) {
        Bitmap BitmapOrg = bitmap;
        int width = BitmapOrg.getWidth();
        int height = BitmapOrg.getHeight();

        if (width > w) {
            float scaleWidth = ((float) w) / width;
            float scaleHeight = ((float) h) / height + 24;
            Matrix matrix = new Matrix();
            matrix.postScale(scaleWidth, scaleWidth);
            Bitmap resizedBitmap = Bitmap.createBitmap(BitmapOrg, 0, 0, width,
                    height, matrix, true);
            return resizedBitmap;
        } else {
            Bitmap resizedBitmap = Bitmap.createBitmap(w, height + 24, Config.RGB_565);
            Canvas canvas = new Canvas(resizedBitmap);
            Paint paint = new Paint();
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(bitmap, (w - width) / 2, 0, paint);
            return resizedBitmap;
        }
    }

    private static String hexStr = "0123456789ABCDEF";

    private static String[] binaryArray = {"0000", "0001", "0010", "0011",
        "0100", "0101", "0110", "0111", "1000", "1001", "1010", "1011",
        "1100", "1101", "1110", "1111"};

    public static byte[] decodeBitmap(Bitmap bmp) {
        int bmpWidth = bmp.getWidth();
        int bmpHeight = bmp.getHeight();
        List<String> list = new ArrayList<String>(); //binaryString list
        StringBuffer sb;
        int bitLen = bmpWidth / 8;
        int zeroCount = bmpWidth % 8;
        String zeroStr = "";
        if (zeroCount > 0) {
            bitLen = bmpWidth / 8 + 1;
            for (int i = 0; i < (8 - zeroCount); i++) {
                zeroStr = zeroStr + "0";
            }
        }

        for (int i = 0; i < bmpHeight; i++) {
            sb = new StringBuffer();
            for (int j = 0; j < bmpWidth; j++) {
                int color = bmp.getPixel(j, i);

                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                // if color close to white，bit='0', else bit='1'
                if (r > 160 && g > 160 && b > 160) {
                    sb.append("0");
                } else {
                    sb.append("1");
                }
            }
            if (zeroCount > 0) {
                sb.append(zeroStr);
            }
            list.add(sb.toString());
        }

        List<String> bmpHexList = binaryListToHexStringList(list);
        String commandHexString = "1D763000";
        String widthHexString = Integer.toHexString(bmpWidth % 8 == 0 ? bmpWidth / 8 : (bmpWidth / 8 + 1));
        if (widthHexString.length() > 2) {
            Log.d(LOG_TAG, "DECODEBITMAP ERROR : width is too large");
            return null;
        } else if (widthHexString.length() == 1) {
            widthHexString = "0" + widthHexString;
        }
        widthHexString = widthHexString + "00";

        String heightHexString = Integer.toHexString(bmpHeight);
        if (heightHexString.length() > 2) {
            Log.d(LOG_TAG, "DECODEBITMAP ERROR : height is too large");
            return null;
        } else if (heightHexString.length() == 1) {
            heightHexString = "0" + heightHexString;
        }
        heightHexString = heightHexString + "00";

        List<String> commandList = new ArrayList<String>();
        commandList.add(commandHexString + widthHexString + heightHexString);
        commandList.addAll(bmpHexList);

        return hexList2Byte(commandList);
    }

    public static List<String> binaryListToHexStringList(List<String> list) {
        List<String> hexList = new ArrayList<String>();
        for (String binaryStr : list) {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < binaryStr.length(); i += 8) {
                String str = binaryStr.substring(i, i + 8);

                String hexString = myBinaryStrToHexString(str);
                sb.append(hexString);
            }
            hexList.add(sb.toString());
        }
        return hexList;

    }

    public static String myBinaryStrToHexString(String binaryStr) {
        String hex = "";
        String f4 = binaryStr.substring(0, 4);
        String b4 = binaryStr.substring(4, 8);
        for (int i = 0; i < binaryArray.length; i++) {
            if (f4.equals(binaryArray[i])) {
                hex += hexStr.substring(i, i + 1);
            }
        }
        for (int i = 0; i < binaryArray.length; i++) {
            if (b4.equals(binaryArray[i])) {
                hex += hexStr.substring(i, i + 1);
            }
        }

        return hex;
    }

    public static byte[] hexList2Byte(List<String> list) {
        List<byte[]> commandList = new ArrayList<byte[]>();

        for (String hexStr : list) {
            commandList.add(hexStringToBytes(hexStr));
        }
        byte[] bytes = sysCopy(commandList);
        return bytes;
    }

    public static byte[] sysCopy(List<byte[]> srcArrays) {
        int len = 0;
        for (byte[] srcArray : srcArrays) {
            len += srcArray.length;
        }
        byte[] destArray = new byte[len];
        int destLen = 0;
        for (byte[] srcArray : srcArrays) {
            System.arraycopy(srcArray, 0, destArray, destLen, srcArray.length);
            destLen += srcArray.length;
        }
        return destArray;
    }


    /**
     * Functions of bixolon
     */

    private boolean start(final Context context) {
        this.context = context;
        // example
        String name = "SICU-151";
        String address = "74:F0:7D:E6:29:F6";
    
        bxlConfigLoader = new BXLConfigLoader(context);
        try {
          bxlConfigLoader.openFile();
        } catch (Exception e) {
          e.printStackTrace();
          bxlConfigLoader.newFile();
        }
        posPrinter = new POSPrinter(context);
    
        posPrinter.addErrorListener(new ErrorListener() {
          @Override
          public void errorOccurred(final ErrorEvent errorEvent) {
            Activity activity = (Activity) context;
            activity.runOnUiThread(new Runnable() {
    
              @Override
              public void run() {
    
                Toast.makeText(context, "Error status : " + getERMessage(errorEvent.getErrorCodeExtended()),
                    Toast.LENGTH_SHORT).show();
    
                if (getERMessage(errorEvent.getErrorCodeExtended()).equals("Power off")) {
                  try {
                    posPrinter.close();
                  } catch (JposException e) {
                    e.printStackTrace();
                    Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
                  }
                  // port-close
                } else if (getERMessage(errorEvent.getErrorCodeExtended()).equals("Cover open")) {
                  // re-print
                } else if (getERMessage(errorEvent.getErrorCodeExtended()).equals("Paper empty")) {
                  // re-print
                }
              }
            });
          }
        });
        posPrinter.addOutputCompleteListener(new OutputCompleteListener() {
          @Override
          public void outputCompleteOccurred(final OutputCompleteEvent outputCompleteEvent) {
            Activity activity = (Activity) context;
            activity.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                Toast.makeText(context, "complete print", Toast.LENGTH_SHORT).show();
              }
            });
          }
        });
        posPrinter.addStatusUpdateListener(new StatusUpdateListener() {
          @Override
          public void statusUpdateOccurred(final StatusUpdateEvent statusUpdateEvent) {
            Activity activity = (Activity) context;
            activity.runOnUiThread(new Runnable() {
    
              @Override
              public void run() {
                Toast.makeText(context, "printer status : " + getSUEMessage(statusUpdateEvent.getStatus()),
                    Toast.LENGTH_SHORT).show();
    
                if (getSUEMessage(statusUpdateEvent.getStatus()).equals("Power off")) {
                  Toast.makeText(context, "check the printer - Power off", Toast.LENGTH_SHORT).show();
                } else if (getSUEMessage(statusUpdateEvent.getStatus()).equals("Cover Open")) {
                  // display message
                  Toast.makeText(context, "check the printer - Cover Open", Toast.LENGTH_SHORT).show();
                } else if (getSUEMessage(statusUpdateEvent.getStatus()).equals("Cover OK")) {
                  // re-print
                } else if (getSUEMessage(statusUpdateEvent.getStatus()).equals("Receipt Paper Empty")) {
                  // display message
                  Toast.makeText(context, "check the printer - Receipt Paper Empty", Toast.LENGTH_SHORT).show();
                } else if (getSUEMessage(statusUpdateEvent.getStatus()).equals("Receipt Paper OK")) {
                  // re-print
                }
              }
            });
          }
        });
    
        try {
          for (Object entry : bxlConfigLoader.getEntries()) {
            JposEntry jposEntry = (JposEntry) entry;
            bxlConfigLoader.removeEntry(jposEntry.getLogicalName());
          }
        } catch (Exception e) {
          e.printStackTrace();
          return false;
        }
    
        try {
          logicalName = setProductName("SICU-151");
          bxlConfigLoader.addEntry(logicalName, BXLConfigLoader.DEVICE_CATEGORY_POS_PRINTER, logicalName,
              BXLConfigLoader.DEVICE_BUS_BLUETOOTH, address);
    
          bxlConfigLoader.saveFile();
        } catch (Exception e) {
          e.printStackTrace();
          return false;
        }
    
        return true;
      }

      public void printPair(final Context context, String label, String value) {
        this.context = context;
        if (start(context)) {
    
          try {
            posPrinter.open(logicalName);
            posPrinter.claim(0);
            posPrinter.setDeviceEnabled(true);
    
            posPrinter.setCharacterEncoding(BXLConst.CE_UTF8);
    
            String ESC = new String(new byte[] { 0x1b, 0x7c });
            String LF = "\n";
    
            posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, label + ":       " + value + LF);
    
          } catch (JposException e) {
            e.printStackTrace();
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
          } finally {
            try {
              posPrinter.close();
            } catch (JposException e) {
              e.printStackTrace();
            }
          }
        }
      }
    
      public void printImage(final Context context, String path) {
        Log.v("RSULT:", path);
        this.context = context;
        if (start(context)) {
          if (openPrinter()) {
            InputStream is = null;
            try {
              ByteBuffer buffer = ByteBuffer.allocate(4);
              buffer.put((byte) POSPrinterConst.PTR_S_RECEIPT);
              buffer.put((byte) brightness);
              buffer.put((byte) compress);
              buffer.put((byte) 0x00);
              Log.v("inputstream", "buffer");
              posPrinter.printBitmap(buffer.getInt(0), path, posPrinter.getRecLineWidth(), POSPrinterConst.PTR_BM_LEFT);
    
            } catch (JposException e) {
              e.printStackTrace();
              Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
            } finally {
              if (is != null) {
                Log.v("inputstream", "aqui");
                try {
                  is.close();
                } catch (IOException e) {
                  e.printStackTrace();
                }
              }
            }
            closePrinter();
          }
        }
      }
    
      private static String getERMessage(int status) {
        switch (status) {
        case POSPrinterConst.JPOS_EPTR_COVER_OPEN:
          return "Cover open";
    
        case POSPrinterConst.JPOS_EPTR_REC_EMPTY:
          return "Paper empty";
    
        case JposConst.JPOS_SUE_POWER_OFF_OFFLINE:
          return "Power off";
    
        default:
          return "Unknown";
        }
      }
    
      private static String getSUEMessage(int status) {
        switch (status) {
        case JposConst.JPOS_SUE_POWER_ONLINE:
          return "Power on";
    
        case JposConst.JPOS_SUE_POWER_OFF_OFFLINE:
          return "Power off";
    
        case POSPrinterConst.PTR_SUE_COVER_OPEN:
          return "Cover Open";
    
        case POSPrinterConst.PTR_SUE_COVER_OK:
          return "Cover OK";
    
        case POSPrinterConst.PTR_SUE_REC_EMPTY:
          return "Receipt Paper Empty";
    
        case POSPrinterConst.PTR_SUE_REC_NEAREMPTY:
          return "Receipt Paper Near Empty";
    
        case POSPrinterConst.PTR_SUE_REC_PAPEROK:
          return "Receipt Paper OK";
    
        case POSPrinterConst.PTR_SUE_IDLE:
          return "Printer Idle";
    
        default:
          return "Unknown";
        }
      }
    
      private String setProductName(String name) {
    
        String productName = BXLConfigLoader.PRODUCT_NAME_SPP_R310;
    
        return productName;
      }
    
      private boolean openPrinter() {
        Log.v("inputstream", "apen");
        try {
          posPrinter.open(logicalName);
          posPrinter.claim(0);
          posPrinter.setDeviceEnabled(true);
          return true;
        } catch (JposException e) {
          e.printStackTrace();
    
          try {
            posPrinter.close();
          } catch (JposException e1) {
            e1.printStackTrace();
          }
        }
        return false;
      }
    
      private void closePrinter() {
        try {
          posPrinter.close();
        } catch (JposException e) {
          e.printStackTrace();
        }
      }
}
