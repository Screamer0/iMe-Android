package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.smedialink.settings.BackupManager;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells2.TextCheckCell3;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.concurrent.TimeUnit;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.PublishSubject;

public class IMeSettingsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private DialogsTabManager tabsManager = DialogsTabManager.getInstance();
    private BackupManager backupManager = BackupManager.Companion.getInstance(getMessagesController(), MessagesController.getInstance(currentAccount).aigramStorage);
    private PublishSubject<Object> settingsChangesSubject = PublishSubject.create();
    private CompositeDisposable disposables = new CompositeDisposable();

    private int tabsHeaderRow;
    private int tabsAllRow;
    private int tabsRowStart;
    private int tabsRowEnd;
    private int tabsDragInfo;

    private int mainRowCount = 0;

    public class TouchHelperCallback extends ItemTouchHelper.Callback {

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (viewHolder.getItemViewType() != 3) {
                return makeMovementFlags(0, 0);
            }
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            if (source.getItemViewType() != target.getItemViewType()) {
                return false;
            }
            listAdapter.swapElements(source.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }

        @Override
        public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                listView.cancelClickRunnables(false);
                viewHolder.itemView.setPressed(true);
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setPressed(false);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        tabsHeaderRow = mainRowCount++;         // Заголовок настройки табов
        tabsAllRow = mainRowCount++;            // Свитч "Все вкладки"
        tabsRowStart = mainRowCount;            // Начало списка табов
        mainRowCount += tabsManager.allTabs.size();
        tabsRowEnd = mainRowCount++;            // Конец списка табов
        tabsDragInfo = mainRowCount++;          // Инфо текст про Drag&Drop

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        tabsManager.refreshActiveTabs();
        tabsManager.storeCurrentTabsData();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.refreshTabState);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.refreshTabIcons);
        disposables.clear();
        super.onFragmentDestroy();
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getInternalString(R.string.iMeSettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setFocusable(true);
        listView.setTag(7);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setLayoutManager(layoutManager);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(listView);
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position == tabsAllRow) {
                changeAllSelection(!allTabsSelected());
                listAdapter.notifyDataSetChanged();
            } else if (position >= tabsRowStart && position < tabsRowEnd) {
                int tabPosition = position - tabsRowStart;
                DialogsTab tab = tabsManager.allTabs.get(tabPosition);
                tab.setEnabled(!tab.isEnabled());
                tabsManager.allTabs.set(tabPosition, tab);
                listAdapter.notifyDataSetChanged();
            }
            settingsChangesSubject.onNext(0);
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        observeChanges();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextCheckCell.class, TextDetailSettingsCell.class, TextSettingsCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),
                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(listView, 0, new Class[]{TextCheckCell3.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
        };
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return mainRowCount;
        }

        @Override
        public long getItemId(int i) {
            if (i < tabsRowStart) {
                return -1;
            }

            i -= tabsRowStart;

            return tabsManager.allTabs.get(i).getPosition();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 1:
                    view = new TextCheckCell3(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new TextCheckCell3(mContext, true);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new TextInfoPrivacyCell(mContext, 21);
                    break;
                default:
                    view = new ShadowSectionCell(mContext);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 1:
                    if (position == tabsAllRow) {
                        ((TextCheckCell3) holder.itemView).setTextAndCheck(LocaleController.getInternalString(R.string.tabAllSettings), allTabsSelected(), true);
                    }
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == tabsHeaderRow) {
                        headerCell.setText(LocaleController.getInternalString(R.string.TabSettings));
                    }
                    break;
                case 3:
                    TextCheckCell3 tabCell = (TextCheckCell3) holder.itemView;
                    if (position >= tabsRowStart && position < tabsRowEnd) {
                        DialogsTab tab = tabsManager.allTabs.get(position - tabsRowStart);
                        int resId = tab.getType().getStringRes();
                        boolean isChecked = tab.isEnabled();
                        tabCell.setTextAndCheck(LocaleController.getInternalString(resId), isChecked, true);
                        tabCell.setIcon(tab.getType().getIconRes());
                    }
                    break;
                case 4:
                    TextInfoPrivacyCell infoCell = (TextInfoPrivacyCell) holder.itemView;
                    infoCell.setTopPadding(0);
                    infoCell.setText(LocaleController.getInternalString(R.string.TabSettingsInfo));
                    break;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position != tabsHeaderRow;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == tabsAllRow) {
                return 1;
            } else if (i == tabsHeaderRow) {
                return 2;
            } else if (i >= tabsRowStart && i < tabsRowEnd) {
                return 3;
            } else if (i == tabsDragInfo) {
                return 4;
            }
            return 0;
        }

        void swapElements(int fromIndex, int toIndex) {
            DialogsTab from = tabsManager.allTabs.get(fromIndex - tabsRowStart);
            DialogsTab to = tabsManager.allTabs.get(toIndex - tabsRowStart);
            from.setPosition(toIndex - tabsRowStart);
            to.setPosition(fromIndex - tabsRowStart);
            tabsManager.allTabs.set(fromIndex - tabsRowStart, to);
            tabsManager.allTabs.set(toIndex - tabsRowStart, from);
            notifyItemMoved(fromIndex, toIndex);
            settingsChangesSubject.onNext(0);
        }
    }

    private void observeChanges() {
        disposables.add(settingsChangesSubject
                .debounce(500, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((o) -> handleChanges(),
                        (t) -> {
                        }));
    }

    private void handleChanges() {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.saveIMeBackup);
    }



    private boolean allTabsSelected() {
        for (DialogsTab tab : tabsManager.allTabs) {
            if (!tab.isEnabled()) {
                return false;
            }
        }
        return true;
    }

    private void changeAllSelection(boolean selected) {
        for (int i = 0; i < tabsManager.allTabs.size(); i++) {
            DialogsTab tab = tabsManager.allTabs.get(i);
            tab.setEnabled(selected);
            tabsManager.allTabs.set(i, tab);
        }
    }
}
