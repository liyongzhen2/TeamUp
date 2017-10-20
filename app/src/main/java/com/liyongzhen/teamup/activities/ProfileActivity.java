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

package com.liyongzhen.teamup.activities;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.TextAppearanceSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.liyongzhen.teamup.R;
import com.liyongzhen.teamup.adapters.PostsByUserAdapter;
import com.liyongzhen.teamup.enums.PostStatus;
import com.liyongzhen.teamup.managers.PostManager;
import com.liyongzhen.teamup.managers.ProfileManager;
import com.liyongzhen.teamup.managers.listeners.OnObjectChangedListener;
import com.liyongzhen.teamup.managers.listeners.OnObjectExistListener;
import com.liyongzhen.teamup.model.Post;
import com.liyongzhen.teamup.model.Profile;
import com.liyongzhen.teamup.utils.LogUtil;
import com.liyongzhen.teamup.utils.LogoutHelper;

public class ProfileActivity extends BaseActivity implements GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = ProfileActivity.class.getSimpleName();
    public static final int CREATE_POST_FROM_PROFILE_REQUEST = 22;
    public static final String USER_ID_EXTRA_KEY = "ProfileActivity.USER_ID_EXTRA_KEY";

    // UI references.
    private TextView nameEditText;
    private ImageView imageView;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView postsCounterTextView;
    private TextView postsLabelTextView;
    private ProgressBar postsProgressBar;

    private FirebaseAuth mAuth;
    private GoogleApiClient mGoogleApiClient;
    private String currentUserId;
    private String userID;

    private PostsByUserAdapter postsAdapter;
    private SwipeRefreshLayout swipeContainer;
    private TextView likesCountersTextView;
    private ProfileManager profileManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        userID = getIntent().getStringExtra(USER_ID_EXTRA_KEY);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            currentUserId = firebaseUser.getUid();
        }

        // Set up the login form.
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        imageView = (ImageView) findViewById(R.id.imageView);
        nameEditText = (TextView) findViewById(R.id.nameEditText);
        postsCounterTextView = (TextView) findViewById(R.id.postsCounterTextView);
        likesCountersTextView = (TextView) findViewById(R.id.likesCountersTextView);
        postsLabelTextView = (TextView) findViewById(R.id.postsLabelTextView);
        postsProgressBar = (ProgressBar) findViewById(R.id.postsProgressBar);

        swipeContainer = (SwipeRefreshLayout) findViewById(R.id.swipeContainer);
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                onRefreshAction();
            }
        });

        loadPostsList();
        supportPostponeEnterTransition();
    }

    @Override
    public void onStart() {
        super.onStart();
        loadProfile();

        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        profileManager.closeListeners(this);

        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.stopAutoManage(this);
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case CreatePostActivity.CREATE_NEW_POST_REQUEST:
                    postsAdapter.loadPosts();
                    showSnackBar(R.string.message_post_was_created);
                    setResult(RESULT_OK);
                    break;

                case PostDetailsActivity.UPDATE_POST_REQUEST:
                    if (data != null) {
                        PostStatus postStatus = (PostStatus) data.getSerializableExtra(PostDetailsActivity.POST_STATUS_EXTRA_KEY);
                        if (postStatus.equals(PostStatus.REMOVED)) {
                            postsAdapter.removeSelectedPost();

                        } else if (postStatus.equals(PostStatus.UPDATED)) {
                            postsAdapter.updateSelectedPost();
                        }
                    }
                    break;
            }
        }
    }

    private void onRefreshAction() {
        postsAdapter.loadPosts();
    }

    private void loadPostsList() {
        if (recyclerView == null) {

            recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
            postsAdapter = new PostsByUserAdapter(this, userID);
            postsAdapter.setCallBack(new PostsByUserAdapter.CallBack() {
                @Override
                public void onItemClick(final Post post, final View view) {
                    PostManager.getInstance(ProfileActivity.this).isPostExistSingleValue(post.getId(), new OnObjectExistListener<Post>() {
                        @Override
                        public void onDataChanged(boolean exist) {
                            if (exist) {
                                openPostDetailsActivity(post, view);
                            } else {
                                showSnackBar(R.string.error_post_was_removed);
                            }
                        }
                    });
                }

                @Override
                public void onPostsListChanged(int postsCount) {
                    String postsLabel = getResources().getQuantityString(R.plurals.posts_counter_format, postsCount, postsCount);
                    postsCounterTextView.setText(buildCounterSpannable(postsLabel, postsCount));

                    likesCountersTextView.setVisibility(View.VISIBLE);
                    postsCounterTextView.setVisibility(View.VISIBLE);

                    if (postsCount > 0) {
                        postsLabelTextView.setVisibility(View.VISIBLE);
                    }

                    swipeContainer.setRefreshing(false);
                    hideLoadingPostsProgressBar();
                }

                @Override
                public void onPostLoadingCanceled() {
                    swipeContainer.setRefreshing(false);
                    hideLoadingPostsProgressBar();
                }
            });

            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
            recyclerView.setAdapter(postsAdapter);
            postsAdapter.loadPosts();
        }
    }

    private Spannable buildCounterSpannable(String label, int value) {
        SpannableStringBuilder contentString = new SpannableStringBuilder();
        contentString.append(String.valueOf(value));
        contentString.append("\n");
        int start = contentString.length();
        contentString.append(label);
        contentString.setSpan(new TextAppearanceSpan(this, R.style.TextAppearance_Second_Light), start, contentString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return contentString;
    }

    private void openPostDetailsActivity(Post post, View v) {
        Intent intent = new Intent(ProfileActivity.this, PostDetailsActivity.class);
        intent.putExtra(PostDetailsActivity.POST_ID_EXTRA_KEY, post.getId());
        intent.putExtra(PostDetailsActivity.AUTHOR_ANIMATION_NEEDED_EXTRA_KEY, true);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            View imageView = v.findViewById(R.id.postImageView);

            ActivityOptions options = ActivityOptions.
                    makeSceneTransitionAnimation(ProfileActivity.this,
                            new android.util.Pair<>(imageView, getString(R.string.post_image_transition_name))
                    );
            startActivityForResult(intent, PostDetailsActivity.UPDATE_POST_REQUEST, options.toBundle());
        } else {
            startActivityForResult(intent, PostDetailsActivity.UPDATE_POST_REQUEST);
        }
    }

    private void loadProfile() {
        profileManager = ProfileManager.getInstance(this);
        profileManager.getProfileValue(ProfileActivity.this, userID, createOnProfileChangedListener());
    }

    private OnObjectChangedListener<Profile> createOnProfileChangedListener() {
        return new OnObjectChangedListener<Profile>() {
            @Override
            public void onObjectChanged(Profile obj) {
                fillUIFields(obj);
            }
        };
    }

    private void fillUIFields(Profile profile) {
        if (profile != null) {
            nameEditText.setText(profile.getUsername());

            if (profile.getPhotoUrl() != null) {
                Glide.with(this)
                        .load(profile.getPhotoUrl())
                        .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                        .crossFade()
                        .error(R.drawable.ic_stub)
                        .listener(new RequestListener<String, GlideDrawable>() {
                            @Override
                            public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
                                scheduleStartPostponedTransition(imageView);
                                progressBar.setVisibility(View.GONE);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                                scheduleStartPostponedTransition(imageView);
                                progressBar.setVisibility(View.GONE);
                                return false;
                            }
                        })
                        .into(imageView);
            } else {
                progressBar.setVisibility(View.GONE);
                imageView.setImageResource(R.drawable.ic_stub);
            }

            int likesCount = (int) profile.getLikesCount();
            String likesLabel = getResources().getQuantityString(R.plurals.likes_counter_format, likesCount, likesCount);
            likesCountersTextView.setText(buildCounterSpannable(likesLabel, likesCount));
        }
    }

    private void hideLoadingPostsProgressBar() {
        if (postsProgressBar.getVisibility() != View.GONE) {
            postsProgressBar.setVisibility(View.GONE);
        }
    }

    private void scheduleStartPostponedTransition(final ImageView imageView) {
        imageView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                imageView.getViewTreeObserver().removeOnPreDrawListener(this);
                supportStartPostponedEnterTransition();
                return true;
            }
        });
    }

    private void startMainActivity() {
        Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void startEditProfileActivity() {
        if (hasInternetConnection()) {
            Intent intent = new Intent(ProfileActivity.this, EditProfileActivity.class);
            startActivity(intent);
        } else {
            showSnackBar(R.string.internet_connection_failed);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        LogUtil.logDebug(TAG, "onConnectionFailed:" + connectionResult);
    }

    private void openCreatePostActivity() {
        Intent intent = new Intent(this, CreatePostActivity.class);
        startActivityForResult(intent, CreatePostActivity.CREATE_NEW_POST_REQUEST);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (userID.equals(currentUserId)) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.profile_menu, menu);
            return true;
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.editProfile:
                startEditProfileActivity();
                return true;
            case R.id.signOut:
                LogoutHelper.signOut(mGoogleApiClient, this);
                startMainActivity();
                return true;
            case R.id.createPost:
                if (hasInternetConnection()) {
                    openCreatePostActivity();
                } else {
                    showSnackBar(R.string.internet_connection_failed);
                }
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
