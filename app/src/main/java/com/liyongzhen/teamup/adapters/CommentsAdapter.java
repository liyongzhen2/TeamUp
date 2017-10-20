/*
 * Copyright 2017 Rozdoum
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.liyongzhen.teamup.adapters;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.liyongzhen.teamup.R;
import com.liyongzhen.teamup.managers.ProfileManager;
import com.liyongzhen.teamup.managers.listeners.OnObjectChangedListener;
import com.liyongzhen.teamup.model.Comment;
import com.liyongzhen.teamup.model.Profile;
import com.liyongzhen.teamup.utils.FormatterUtil;
import com.liyongzhen.teamup.views.ExpandableTextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by alexey on 15.11.16.
 */

public class CommentsAdapter {
    private static final String TAG = CommentsAdapter.class.getName();

    private ViewGroup parent;
    private List<Comment> commentList = new ArrayList<>();
    private LayoutInflater inflater;
    private ProfileManager profileManager;
    private OnAuthorClickListener onAuthorClickListener;


    public CommentsAdapter(ViewGroup parent, OnAuthorClickListener onAuthorClickListener) {
        this.parent = parent;
        this.onAuthorClickListener = onAuthorClickListener;
        inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        profileManager = ProfileManager.getInstance(parent.getContext().getApplicationContext());
    }

    private void initListView() {
        parent.removeAllViews();
        for (int i = 0; i < commentList.size(); i++) {
            inflater.inflate(R.layout.custom_divider, parent, true);
            Comment comment = commentList.get(i);
            parent.addView(getView(comment));
        }
    }

    private View getView(Comment comment) {
        final View convertView = inflater.inflate(R.layout.comment_list_item, parent, false);

        ImageView avatarImageView = (ImageView) convertView.findViewById(R.id.avatarImageView);
        ExpandableTextView commentTextView = (ExpandableTextView) convertView.findViewById(R.id.commentText);
        TextView dateTextView = (TextView) convertView.findViewById(R.id.dateTextView);

        final String authorId = comment.getAuthorId();
        if (authorId != null)
            profileManager.getProfileSingleValue(authorId, createOnProfileChangeListener(commentTextView,
                    avatarImageView, comment.getText()));

        commentTextView.setText(comment.getText());

        CharSequence date = FormatterUtil.getRelativeTimeSpanString(parent.getContext(), comment.getCreatedDate());
        dateTextView.setText(date);

        avatarImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAuthorClickListener.onAuthorClick(authorId, v);
            }
        });

        return convertView;
    }

    private OnObjectChangedListener<Profile> createOnProfileChangeListener(final ExpandableTextView expandableTextView, final ImageView avatarImageView, final String comment) {
        return new OnObjectChangedListener<Profile>() {
            @Override
            public void onObjectChanged(Profile obj) {
                String userName = obj.getUsername();
                fillComment(userName, comment, expandableTextView);

                if (obj.getPhotoUrl() != null) {
                    Glide.with(parent.getContext())
                            .load(obj.getPhotoUrl())
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .crossFade()
                            .error(R.drawable.ic_stub)
                            .into(avatarImageView);
                }
            }
        };
    }

    private void fillComment(String userName, String comment, ExpandableTextView commentTextView) {
        Spannable contentString = new SpannableStringBuilder(userName + "   " + comment);
        contentString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(parent.getContext(), R.color.highlight_text)),
                0, userName.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        commentTextView.setText(contentString);
    }

    public void setList(List<Comment> commentList) {
        this.commentList = commentList;
        initListView();
    }

    public interface OnAuthorClickListener {
        public void onAuthorClick(String authorId, View view);
    }
}
