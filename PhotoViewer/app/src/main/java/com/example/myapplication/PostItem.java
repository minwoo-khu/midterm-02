package com.example.myapplication;

import android.graphics.Bitmap;

public class PostItem {
    public String title;
    public String author;
    public String createdAt;
    public String imageUrl;   // 고유 키로 사용
    public Bitmap bitmap;

    public PostItem(String title, String author, String createdAt, String imageUrl, Bitmap bitmap) {
        this.title = title;
        this.author = author;
        this.createdAt = createdAt;
        this.imageUrl = imageUrl;
        this.bitmap = bitmap;
    }

    /** 즐겨찾기 고유키: 이미지 URL을 기본 키로 사용 */
    public String favKey() {
        return imageUrl != null ? imageUrl : (title + "|" + createdAt);
    }
}
