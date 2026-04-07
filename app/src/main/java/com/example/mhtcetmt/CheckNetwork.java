package com.example.mhtcetmt;


import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class CheckNetwork {

    private static final String TAG = CheckNetwork.class.getSimpleName();

    public static boolean isInternetAvailable(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm != null) {
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                Log.d(TAG, "Internet connection available");
                return true;
            }
        }

        Log.d(TAG, "No internet connection");
        return false;
    }
}
