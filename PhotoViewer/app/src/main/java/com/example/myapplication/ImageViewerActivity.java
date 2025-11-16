package com.example.myapplication;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.OutputStream;

public class ImageViewerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("이미지 보기");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_save) {
                saveToGallery();
                return true;
            }
            return false;
        });

        ImageView iv = findViewById(R.id.ivFull);
        Bitmap b = BitmapHolder.get();
        if (b != null) iv.setImageBitmap(b);
    }

    private void saveToGallery() {
        Bitmap b = BitmapHolder.get();
        if (b == null) { Toast.makeText(this, "이미지가 없습니다", Toast.LENGTH_SHORT).show(); return; }

        try {
            String name = "post_" + System.currentTimeMillis() + ".jpg";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyApp");
            }

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new RuntimeException("URI null");

            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                b.compress(Bitmap.CompressFormat.JPEG, 95, os);
            }
            Toast.makeText(this, "갤러리에 저장됨", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "저장 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BitmapHolder.clear();
    }
}
