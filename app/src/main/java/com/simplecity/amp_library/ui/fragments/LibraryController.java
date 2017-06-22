package com.simplecity.amp_library.ui.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.util.Pair;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.simplecity.amp_library.R;
import com.simplecity.amp_library.ShuttleApplication;
import com.simplecity.amp_library.model.Album;
import com.simplecity.amp_library.model.AlbumArtist;
import com.simplecity.amp_library.model.CategoryItem;
import com.simplecity.amp_library.model.Genre;
import com.simplecity.amp_library.model.Playlist;
import com.simplecity.amp_library.search.SearchFragment;
import com.simplecity.amp_library.ui.activities.ToolbarListener;
import com.simplecity.amp_library.ui.adapters.PagerAdapter;
import com.simplecity.amp_library.ui.detail.AlbumDetailFragment;
import com.simplecity.amp_library.ui.detail.ArtistDetailFragment;
import com.simplecity.amp_library.ui.detail.BaseDetailFragment;
import com.simplecity.amp_library.ui.detail.GenreDetailFragment;
import com.simplecity.amp_library.ui.detail.PlaylistDetailFragment;
import com.simplecity.amp_library.ui.drawer.DrawerEventRelay;
import com.simplecity.amp_library.ui.views.ContextualToolbar;
import com.simplecity.amp_library.ui.views.ContextualToolbarHost;
import com.simplecity.amp_library.ui.views.PagerListenerAdapter;
import com.simplecity.amp_library.ui.views.multisheet.MultiSheetEventRelay;
import com.simplecity.amp_library.utils.DialogUtils;
import com.simplecity.amp_library.utils.SettingsManager;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import test.com.androidnavigation.fragment.FragmentInfo;
import test.com.multisheetview.ui.view.MultiSheetView;


public class LibraryController extends BaseFragment implements
        AlbumArtistFragment.AlbumArtistClickListener,
        AlbumFragment.AlbumClickListener,
        SuggestedFragment.SuggestedClickListener,
        PlaylistFragment.PlaylistClickListener,
        GenreFragment.GenreClickListener,
        ContextualToolbarHost {

    private static final String TAG = "LibraryController";

    private PagerAdapter adapter;

    @BindView(R.id.tabs)
    TabLayout slidingTabLayout;

    @BindView(R.id.pager)
    ViewPager pager;

    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @BindView(R.id.contextualToolbar)
    ContextualToolbar contextualToolbar;

    @Inject DrawerEventRelay drawerEventRelay;

    private int currentPage = 0;

    public static FragmentInfo fragmentInfo() {
        return new FragmentInfo(LibraryController.class, null, "LibraryController");
    }

    public LibraryController() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ShuttleApplication.getInstance().getAppComponent().inject(this);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        CategoryItem.getCategoryItems(sharedPreferences);

        adapter = new PagerAdapter(getChildFragmentManager());

        List<CategoryItem> categoryItems = Stream.of(CategoryItem.getCategoryItems(sharedPreferences))
                .filter(categoryItem -> categoryItem.isEnabled)
                .collect(Collectors.toList());

        int defaultPageType = SettingsManager.getInstance().getDefaultPageType();
        int defaultPage = 1;
        for (int i = 0; i < categoryItems.size(); i++) {
            CategoryItem categoryItem = categoryItems.get(i);
            adapter.addFragment(categoryItem.getFragment(getContext()));
            if (categoryItem.type == defaultPageType) {
                defaultPage = i;
            }
        }

        currentPage = Math.min(defaultPage, adapter.getCount());

        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_library, container, false);

        ButterKnife.bind(this, rootView);

        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);

        pager.setAdapter(adapter);
        pager.setOffscreenPageLimit(adapter.getCount() - 1);
        pager.addOnPageChangeListener(new PagerListenerAdapter() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                for (int i = 0; i < adapter.getCount(); i++) {
                    Fragment fragment = adapter.getItem(i);
                    if (fragment instanceof PageSelectedListener) {
                        if (i == position) {
                            ((PageSelectedListener) fragment).onPageSelected();
                        } else if (i == currentPage) {
                            ((PageSelectedListener) fragment).onPageDeselected();
                        }
                    }
                }
                currentPage = position;
            }
        });
        pager.setCurrentItem(currentPage);

        slidingTabLayout.setupWithViewPager(pager);

        pager.postDelayed(() -> {
            if (pager != null) {
                DialogUtils.showRateSnackbar(getActivity(), pager);
            }
        }, 1000);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getActivity() instanceof ToolbarListener) {
            ((ToolbarListener) getActivity()).toolbarAttached((Toolbar) view.findViewById(R.id.toolbar));
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        multiSheetEventRelay.sendEvent(new MultiSheetEventRelay.MultiSheetEvent(MultiSheetEventRelay.MultiSheetEvent.Action.SHOW_IF_HIDDEN, MultiSheetView.Sheet.NONE));

        drawerEventRelay.sendEvent(new DrawerEventRelay.DrawerEvent(DrawerEventRelay.DrawerEvent.Type.LIBRARY_SELECTED, null, false));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.menu_library, menu);
        setupCastMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_search:
                openSearch();
                return true;
        }
        return false;
    }

    private void openSearch() {
        getNavigationController().pushViewController(SearchFragment.newInstance(null), "SearchFragment");
    }

    @Override
    public void onAlbumArtistClicked(AlbumArtist albumArtist, View transitionView) {
        String transitionName = ViewCompat.getTransitionName(transitionView);
        ArtistDetailFragment detailFragment = ArtistDetailFragment.newInstance(albumArtist, transitionName);
        pushDetailFragment(detailFragment, transitionView);
    }

    @Override
    public void onAlbumClicked(Album album, View transitionView) {
        String transitionName = ViewCompat.getTransitionName(transitionView);
        AlbumDetailFragment detailFragment = AlbumDetailFragment.newInstance(album, transitionName);
        pushDetailFragment(detailFragment, transitionView);
    }

    @Override
    public void onGenreClicked(Genre genre) {
        pushDetailFragment(GenreDetailFragment.newInstance(genre), null);
    }

    @Override
    public void onPlaylistClicked(Playlist playlist) {
        pushDetailFragment(PlaylistDetailFragment.newInstance(playlist), null);
    }

    void pushDetailFragment(BaseDetailFragment detailFragment, @Nullable View transitionView) {

        List<Pair<View, String>> transitions = new ArrayList<>();

        if (transitionView != null) {
            String transitionName = ViewCompat.getTransitionName(transitionView);
            transitions.add(new Pair<>(transitionView, transitionName));

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                Transition moveTransition = TransitionInflater.from(getContext()).inflateTransition(R.transition.image_transition);
                detailFragment.setSharedElementEnterTransition(moveTransition);
                detailFragment.setSharedElementReturnTransition(moveTransition);
            }
        }

        getNavigationController().pushViewController(detailFragment, "DetailFragment", transitions);
    }

    @Override
    protected String screenName() {
        return "LibraryController";
    }

    @Override
    public ContextualToolbar getContextualToolbar() {
        return contextualToolbar;
    }
}