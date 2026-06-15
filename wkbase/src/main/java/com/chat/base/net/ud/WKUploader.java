package com.chat.base.net.ud;


import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.net.ApiService;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.UploadFileUrl;
import com.chat.base.net.entity.UploadResultEntity;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.WKTimeUtils;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public class WKUploader extends WKBaseModel {
    private WKUploader() {
    }

    private static class UploadBinder {
        final static WKUploader upload = new WKUploader();
    }

    public static WKUploader getInstance() {
        return UploadBinder.upload;
    }

    public void upload(String uploadUrl, String filePath, final IUploadBack iUploadBack) {
        upload(uploadUrl, filePath, filePath, iUploadBack);
    }

    public void upload(String uploadUrl, String filePath, Object tag, final IUploadBack iUploadBack) {
        uploadInternal(uploadUrl, filePath, tag, false, iUploadBack);
    }

    public void uploadPut(String uploadUrl, String filePath, final IUploadBack iUploadBack) {
        uploadInternal(uploadUrl, filePath, filePath, true, iUploadBack);
    }

    public void uploadNoResult(String uploadUrl, String filePath, final IUploadBack iUploadBack) {
        MediaType mediaType = MediaType.Companion.parse("multipart/form-data");
        File file = new File(filePath);
        RequestBody fileBody = RequestBody.Companion.create(file, mediaType);
        FileRequestBody fileRequestBody = new FileRequestBody(fileBody, filePath);
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), fileRequestBody);
        String token = WKConfig.getInstance().getToken();
        request(createService(UploadService.class).uploadRawWithToken(uploadUrl, token, part), new IRequestResultListener<>() {
            @Override
            public void onSuccess(ResponseBody result) {
                if (iUploadBack != null) {
                    iUploadBack.onSuccess("");
                }
            }

            @Override
            public void onFail(int code, String msg) {
                WKLogUtils.e("WKUploader", "upload raw failed url=" + uploadUrl + " code=" + code + " msg=" + msg);
                if (iUploadBack != null) {
                    iUploadBack.onError();
                }
            }
        });
    }

    private void uploadInternal(String uploadUrl, String filePath, Object tag, boolean usePut, final IUploadBack iUploadBack) {
        MediaType mediaType = MediaType.Companion.parse("multipart/form-data");
        File file = new File(filePath);
        RequestBody fileBody = RequestBody.Companion.create(file, mediaType);
        FileRequestBody fileRequestBody = new FileRequestBody(fileBody, tag);
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), fileRequestBody);
        String token = WKConfig.getInstance().getToken();
        UploadService uploadService = createService(UploadService.class);
        request(usePut ? uploadService.uploadPutWithToken(uploadUrl, token, part) : uploadService.uploadWithToken(uploadUrl, token, part), new IRequestResultListener<>() {
            @Override
            public void onSuccess(UploadResultEntity result) {
                if (iUploadBack != null ) {
                    iUploadBack.onSuccess(result.path);
                }
            }

            @Override
            public void onFail(int code, String msg) {
                WKLogUtils.e("WKUploader", "upload failed url=" + uploadUrl + " code=" + code + " msg=" + msg);
                if (iUploadBack != null) {
                    iUploadBack.onError();
                }
            }
        });
    }

    public void getUploadFileUrl(String channelID, byte channelType, String localPath, final IGetUploadFileUrl iGetUploadFileUrl) {
        File f = new File(localPath);
        String tempFileName = f.getName();
        String prefix = tempFileName.substring(tempFileName.lastIndexOf(".") + 1);
        String path = "/" + channelType + "/" + channelID + "/" + WKTimeUtils.getInstance().getCurrentMills() + "." + prefix;
        request(createService(ApiService.class).getUploadFileUrl(WKApiConfig.baseUrl + "file/upload?type=chat&path=" + path), new IRequestResultListener<UploadFileUrl>() {
            @Override
            public void onSuccess(UploadFileUrl result) {
                iGetUploadFileUrl.onResult(result.url, path);
            }

            @Override
            public void onFail(int code, String msg) {
                iGetUploadFileUrl.onResult(null, path);
            }
        });
    }


    public interface IGetUploadFileUrl {
        void onResult(String url, String fileUrl);
    }

    public interface IUploadBack {
        void onSuccess(String url);

        void onError();
    }
}
