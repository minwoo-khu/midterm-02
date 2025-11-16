package com.example.myapplication;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private TextView textView;
    private SwipeRefreshLayout swipe;
    private RecyclerView recyclerView;
    private LinearLayout emptyView;

    private final String site_url = "http://10.0.2.2:8000";
    private CloadImage taskDownload;

    // 즐겨찾기 관련
    private FavoritesStore favs;
    private android.widget.Switch switchFavOnly;
    private final List<PostItem> items = new ArrayList<>();

    // 이미지 탭 시 전체화면으로 보기 (BitmapHolder 사용)
    private final ImageAdapter.OnItemClickListener onItemClickListener = bitmap -> {
        BitmapHolder.set(bitmap);
        startActivity(new Intent(MainActivity.this, ImageViewerActivity.class));
    };

    // UploadActivity 결과 받기 → 업로드 성공 시 자동 새로고침
    private final ActivityResultLauncher<Intent> uploadLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    reload();
                    Toast.makeText(getApplicationContext(), "업로드 완료, 목록 갱신", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView     = findViewById(R.id.textView);
        swipe        = findViewById(R.id.swipe);
        recyclerView = findViewById(R.id.recyclerView);
        emptyView    = findViewById(R.id.emptyView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Pull-to-Refresh
        swipe.setOnRefreshListener(this::reload);

        // 빈 화면 "재시도"
        findViewById(R.id.btnRetry).setOnClickListener(v -> reload());

        // 즐겨찾기 기능
        favs = new FavoritesStore(this);
        switchFavOnly = findViewById(R.id.switchFavOnly);
        if (switchFavOnly != null) {
            switchFavOnly.setOnCheckedChangeListener((buttonView, isChecked) -> applyFilterAndRender());
        }
    }

    // 동기화 버튼(onClick)
    public void onClickDownload(View v) { reload(); }

    // 업로드 화면 열기(onClick)
    public void onClickUpload(View v) {
        Intent it = new Intent(MainActivity.this, UploadActivity.class);
        uploadLauncher.launch(it);
    }

    private void reload() {
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        taskDownload = new CloadImage();
        taskDownload.execute(site_url + "/api_root/Post/");
        swipe.setRefreshing(true);
    }

    private void showEmpty(boolean show, String msg) {
        emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.emptyText)).setText(msg);
    }

    /** 즐겨찾기 필터 적용 후 렌더링 */
    private void applyFilterAndRender() {
        boolean favOnly = (switchFavOnly != null) && switchFavOnly.isChecked();

        List<PostItem> view = new ArrayList<>();
        if (favOnly) {
            for (PostItem it : items) {
                if (favs.isFav(it.favKey())) view.add(it);
            }
        } else {
            view.addAll(items);
        }

        textView.setText("이미지 " + view.size() + "개" + (favOnly ? " (즐겨찾기)" : ""));
        if (view.isEmpty()) {
            showEmpty(true, favOnly ? "즐겨찾기한 이미지가 없습니다." : "불러올 이미지가 없습니다.");
        } else {
            showEmpty(false, "");
            recyclerView.setAdapter(new ImageAdapter(view, onItemClickListener, favs, favOnly));
        }
    }

    /** 서버에서 목록을 받아와 PostItem 리스트로 구성 */
    private class CloadImage extends AsyncTask<String, Integer, List<PostItem>> {
        @Override
        protected List<PostItem> doInBackground(String... urls) {
            List<PostItem> list = new ArrayList<>();
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            InputStream is = null;

            try {
                URL url = new URL(site_url + "/api_root/Post/?format=json");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "GET status=" + responseCode);
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return list;
                }

                is = conn.getInputStream();
                reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) result.append(line);

                JSONArray aryJson = new JSONArray(result.toString());
                for (int i = 0; i < aryJson.length(); i++) {
                    JSONObject obj = aryJson.getJSONObject(i);

                    // 메타데이터(있는 경우만 사용)
                    String title     = obj.optString("title", "");
                    String author    = obj.optString("author", "");
                    String createdAt = obj.optString("created_at", obj.optString("created", ""));
                    String imageUrl  = obj.optString("image", "");
                    if (imageUrl == null || imageUrl.isEmpty()) continue;

                    // URL 보정
                    if (!imageUrl.startsWith("http")) {
                        if (!imageUrl.startsWith("/")) imageUrl = "/" + imageUrl;
                        imageUrl = site_url + imageUrl;
                    } else {
                        imageUrl = imageUrl
                                .replace("http://127.0.0.1", site_url)
                                .replace("http://localhost", site_url)
                                .replace("https://127.0.0.1", site_url)
                                .replace("https://localhost", site_url);
                    }

                    // 이미지 다운로드
                    URL imgURL = new URL(imageUrl);
                    HttpURLConnection imgConn = (HttpURLConnection) imgURL.openConnection();
                    imgConn.setConnectTimeout(5000);
                    imgConn.setReadTimeout(5000);
                    Bitmap bmp = null;
                    try (InputStream imgStream = imgConn.getInputStream()) {
                        bmp = BitmapFactory.decodeStream(imgStream);
                    } finally {
                        imgConn.disconnect();
                    }

                    if (bmp != null) {
                        list.add(new PostItem(title, author, createdAt, imageUrl, bmp));
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "CloadImage error", e);
            } finally {
                try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }
            return list;
        }

        @Override
        protected void onPostExecute(List<PostItem> loaded) {
            if (isFinishing() || isDestroyed()) return;
            swipe.setRefreshing(false);

            items.clear();
            if (loaded != null) items.addAll(loaded);

            applyFilterAndRender();
        }
    }
}
