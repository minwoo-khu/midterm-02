package com.example.myapplication;

import android.content.ContentResolver;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadActivity extends AppCompatActivity {

    private static final String BASE_URL = "http://10.0.2.2:8000";
    private static final String POST_URL = BASE_URL + "/api_root/Post/";

    private ImageView ivPreview;
    private EditText etCaption, etTitle, etAuthor;
    private Button btnPick, btnUpload;
    private ProgressBar progress;

    @Nullable private Uri pickedUri;
    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    pickedUri = uri;
                    try {
                        ContentResolver cr = getContentResolver();
                        try (InputStream in = cr.openInputStream(uri)) {
                            ivPreview.setImageBitmap(BitmapFactory.decodeStream(in));
                        }
                        btnUpload.setEnabled(true);
                    } catch (Exception e) {
                        Toast.makeText(this, "이미지 로드 실패", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        etTitle   = findViewById(R.id.etTitle);
        etAuthor  = findViewById(R.id.etAuthor);  // 숫자(ID) 또는 username 입력
        ivPreview = findViewById(R.id.ivPreview);
        etCaption = findViewById(R.id.etCaption);
        btnPick   = findViewById(R.id.btnPick);
        btnUpload = findViewById(R.id.btnUpload);
        progress  = findViewById(R.id.progress);

        btnPick.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnUpload.setOnClickListener(v -> {
            if (pickedUri == null) {
                Toast.makeText(this, "이미지를 먼저 선택하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            String titleInput  = etTitle.getText().toString().trim();
            String authorInput = etAuthor.getText().toString().trim();
            String textInput   = etCaption.getText().toString().trim(); 

            if (titleInput.isEmpty())  { etTitle.setError("필수 항목"); etTitle.requestFocus(); return; }
            if (authorInput.isEmpty()) { etAuthor.setError("필수 항목"); etAuthor.requestFocus(); return; }
            if (textInput.isEmpty())   { etCaption.setError("본문을 입력하세요"); etCaption.requestFocus(); return; }

            uploadNow(titleInput, authorInput, textInput); 
        });
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? android.view.View.VISIBLE : android.view.View.GONE);
        btnPick.setEnabled(!busy);
        btnUpload.setEnabled(!busy && pickedUri != null);
    }

    /**
     * author 입력값을 PK로 정규화:
     * - 숫자면 그대로 반환
     * - 문자열(username)이면 여러 후보 엔드포인트에서 검색 후 첫 match의 id 반환
     *   (배열 응답, paginated 응답 둘 다 지원)
     */
    @Nullable
    private String resolveAuthorId(String input) {
        try {
            // 1) 이미 숫자인 경우 그대로 사용
            if (input.matches("\\d+")) return input;

            // 2) username → id 조회 시도
            String[] candidates = new String[] {
                    "/api_root/users/",   // 보편적
                    "/api_root/User/",    // 대문자 케이스
                    "/users/"             // 백엔드에 따라 다름
            };

            String q = "?search=" + URLEncoder.encode(input, StandardCharsets.UTF_8.name());
            for (String path : candidates) {
                String url = BASE_URL + path + q;
                Request req = new Request.Builder()
                        .url(url)
                        .header("Accept", "application/json")
                        .get()
                        .build();

                try (Response resp = client.newCall(req).execute()) {
                    if (!resp.isSuccessful()) continue;
                    String body = resp.body() != null ? resp.body().string() : "";

                    // 배열 또는 {results:[...]} 형태 모두 지원
                    JSONArray arr = null;
                    try {
                        if (body.trim().startsWith("[")) {
                            arr = new JSONArray(body);
                        } else {
                            JSONObject obj = new JSONObject(body);
                            if (obj.has("results")) arr = obj.optJSONArray("results");
                            else if (obj.has("data")) arr = obj.optJSONArray("data");
                        }
                    } catch (JSONException ignored) {}

                    if (arr != null && arr.length() > 0) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject u = arr.optJSONObject(i);
                            if (u == null) continue;
                            String username = u.optString("username", null);
                            // 일부 API는 'name'을 쓸 수도 있음
                            if (username == null) username = u.optString("name", null);
                            if (username != null && username.equals(input)) {
                                // id / pk 둘 다 시도
                                String idStr = u.optString("id", null);
                                if (idStr == null) idStr = u.optString("pk", null);
                                if (idStr != null && idStr.matches("\\d+")) {
                                    return idStr;
                                }
                            }
                        }
                    }
                } catch (Exception ignore) {
                    // 다음 후보로
                }
            }
        } catch (Exception e) {
            // ignore → null 반환
        }
        return null;
    }

    private void uploadNow(String title, String authorInput, String text) {
        setBusy(true);
        io.execute(() -> {
            try {
                // author 문자열을 PK로 변환
                String authorId = resolveAuthorId(authorInput);
                if (authorId == null) {
                    runOnUiThread(() -> {
                        setBusy(false);
                        etAuthor.setError("작성자(ID) 미발견: 올바른 ID 또는 존재하는 username을 입력");
                        Toast.makeText(this, "작성자 확인 실패", Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                byte[] imageBytes = readAllBytesFromUri(pickedUri);
                String fileName = "upload.jpg";
                String mimeType = getContentResolver().getType(pickedUri);
                if (mimeType == null) mimeType = "image/jpeg";

                ProgressRequestBody.Callback pcb = percent -> {
                    progress.setProgress(percent);
                    if (percent >= 100) progress.setVisibility(android.view.View.GONE);
                };
                RequestBody imageBody =
                        new ProgressRequestBody(imageBytes, MediaType.parse(mimeType), pcb);

                MultipartBody.Builder mb = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("title", title)
                        .addFormDataPart("author", authorId)   // ← 정수 PK로 보냄
                        .addFormDataPart("text", text)
                        .addFormDataPart("image", fileName, imageBody);

                String caption = etCaption.getText().toString().trim();
                if (!caption.isEmpty()) mb.addFormDataPart("caption", caption);

                Request request = new Request.Builder()
                        .url(POST_URL)
                        .header("Accept", "application/json")
                        .post(mb.build())
                        .build();

                try (Response resp = client.newCall(request).execute()) {
                    int code = resp.code();
                    String body = resp.body() != null ? resp.body().string() : "";
                    runOnUiThread(() -> {
                        setBusy(false);
                        if (code == 201 || code == 200) {
                            Toast.makeText(this, "업로드 성공", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            Toast.makeText(this, "업로드 실패: " + code + " / " + body, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, "에러: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private byte[] readAllBytesFromUri(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    /** 업로드 진행률 RequestBody */
    static class ProgressRequestBody extends RequestBody {
        interface Callback { void onProgress(int percent); }

        private final MediaType contentType;
        private final byte[] data;
        @Nullable private final Callback cb;

        ProgressRequestBody(byte[] data, MediaType contentType, @Nullable Callback cb) {
            this.data = data;
            this.contentType = contentType;
            this.cb = cb;
        }

        @Override public MediaType contentType() { return contentType; }
        @Override public long contentLength() { return data.length; }

        @Override
        public void writeTo(okio.BufferedSink sink) throws java.io.IOException {
            final int CHUNK = 8192;
            long written = 0;
            int offset = 0;
            while (offset < data.length) {
                int len = Math.min(CHUNK, data.length - offset);
                sink.write(data, offset, len);
                offset += len;
                written += len;
                if (cb != null) {
                    int percent = (int)((written * 100) / data.length);
                    android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                    h.post(() -> cb.onProgress(percent));
                }
            }
        }
    }
}
