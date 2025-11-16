package com.example.myapplication;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.VH> {

    public interface OnItemClickListener { void onItemClick(Bitmap bitmap); }

    private final List<PostItem> data;
    private final OnItemClickListener listener;
    private final FavoritesStore favs;
    private final boolean filterFavOnly; // true면 즐겨찾기만 보여줄 때 사용(옵션)

    public ImageAdapter(List<PostItem> data, OnItemClickListener listener,
                        FavoritesStore favs, boolean filterFavOnly) {
        this.data = data;
        this.listener = listener;
        this.favs = favs;
        this.filterFavOnly = filterFavOnly;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_image, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PostItem item = data.get(position);
        h.iv.setImageBitmap(item.bitmap);

        // 클릭 → 뷰어 열기
        if (listener != null) h.itemView.setOnClickListener(v -> listener.onItemClick(item.bitmap));

        // 별 상태 바인딩
        bindStar(h.btnFav, favs.isFav(item.favKey()));

        // 별 토글
        h.btnFav.setOnClickListener(v -> {
            boolean nowFav = favs.toggle(item.favKey());
            bindStar(h.btnFav, nowFav);

            // 즐겨찾기 전용 필터 모드라면, 해제 시 즉시 숨김을 원할 수 있음
            if (filterFavOnly && !nowFav) {
                int pos = h.getAbsoluteAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    data.remove(pos);
                    notifyItemRemoved(pos);
                }
            }
        });
    }

    private void bindStar(ImageButton b, boolean fav) {
        b.setImageResource(fav
                ? android.R.drawable.btn_star_big_on
                : android.R.drawable.btn_star_big_off);
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView iv;
        ImageButton btnFav;
        VH(View v) {
            super(v);
            iv = v.findViewById(R.id.imageView);
            btnFav = v.findViewById(R.id.btnFav);
        }
    }
}
