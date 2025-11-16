package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** 즐겨찾기 key(이미지 URL 등)를 로컬에 저장/조회 */
public class FavoritesStore {
    private static final String PREF = "favorites_pref";
    private static final String KEY  = "fav_keys";

    private final SharedPreferences sp;

    public FavoritesStore(Context ctx) {
        sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public Set<String> all() {
        return new HashSet<>(sp.getStringSet(KEY, new HashSet<>()));
    }

    public boolean isFav(String k) {
        return all().contains(k);
    }

    public boolean toggle(String k) {
        Set<String> s = all();
        boolean nowFav;
        if (s.contains(k)) { s.remove(k); nowFav = false; }
        else { s.add(k); nowFav = true; }
        sp.edit().putStringSet(KEY, s).apply();
        return nowFav;
    }
}
