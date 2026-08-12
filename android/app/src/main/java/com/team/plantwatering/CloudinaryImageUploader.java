package com.team.plantwatering;

import android.net.Uri;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import java.util.Map;

public final class CloudinaryImageUploader {
    private static final String UPLOAD_PRESET = "plant_images";

    private CloudinaryImageUploader() {}

    public interface Callback {
        void onSuccess(String imageUrl);
        void onError(String errorMessage);
    }

    public static void upload(Uri imageUri, Callback callback) {
        MediaManager.get()
                .upload(imageUri)
                .unsigned(UPLOAD_PRESET)
                .option("resource_type", "image")
                .callback(new UploadCallback() {
                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        Object secureUrl = resultData.get("secure_url");
                        if (secureUrl != null) {
                            callback.onSuccess(secureUrl.toString());
                        } else {
                            callback.onError("Cloudinary did not return an image URL.");
                        }
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        callback.onError(error.getDescription());
                    }

                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                })
                .dispatch();
    }
}
