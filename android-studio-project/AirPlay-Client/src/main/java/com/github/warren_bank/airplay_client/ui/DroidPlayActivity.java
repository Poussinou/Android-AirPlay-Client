package com.github.warren_bank.airplay_client.ui;

import static com.github.warren_bank.airplay_client.mirror.ScreenMirrorMgr.getProjectionManager;
import static com.github.warren_bank.airplay_client.mirror.ScreenMirrorMgr.setMediaProjection;

import com.github.warren_bank.airplay_client.MainApp;
import com.github.warren_bank.airplay_client.R;
import com.github.warren_bank.airplay_client.constant.Constant;
import com.github.warren_bank.airplay_client.mirror.ScreenMirrorMgr;
import com.github.warren_bank.airplay_client.service.NetworkingService;
import com.github.warren_bank.airplay_client.ui.adapters.FolderBaseAdapter;
import com.github.warren_bank.airplay_client.ui.adapters.FolderGridAdapter;
import com.github.warren_bank.airplay_client.ui.adapters.FolderListAdapter;
import com.github.warren_bank.airplay_client.ui.adapters.NavigationAdapter;
import com.github.warren_bank.airplay_client.ui.adapters.NavigationItem;
import com.github.warren_bank.airplay_client.ui.dialogs.FolderDialog;
import com.github.warren_bank.airplay_client.utils.ExternalStorageUtils;
import com.github.warren_bank.airplay_client.utils.PreferencesMgr;
import com.github.warren_bank.airplay_client.utils.ToastUtils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;
import androidx.drawerlayout.widget.DrawerLayout;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class DroidPlayActivity extends Activity implements AdapterView.OnItemClickListener, FolderDialog.Callback {
  private static final int REQUEST_CODE_SCREEN_CAPTURE = 1;
  private static final int REQUEST_CODE_SETTINGS       = 2;

  private boolean has_airplay_connection;
  private boolean has_storage_permission;

  private Handler handler;

  // screen mirroring
  private boolean canMirror;
  private ScreenMirrorMgr mirror;

  // holder for the navigation "drawer" layout
  private DrawerLayout navigationLayout;

  // holder for the navigation "drawer" adapter
  private NavigationAdapter navigationAdapter;

  // holder for the navigation "drawer" list
  private ListView navigationList;

  // the layout manager to display contents of the selected folder
  private GridView grid;

  // the custom adapter for contents of the selected folder
  private FolderBaseAdapter adapter;

  // ---------------------------------------------------------------------------
  // Activity lifecycle

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.main);

    has_airplay_connection = (MainApp.receiverName != null);
    has_storage_permission = false;

    handler = new DroidPlayHandler(DroidPlayActivity.this);
    MainApp.registerHandler(DroidPlayActivity.class.getName(), handler);

    // action bar
    updateSubtitle();

    canMirror = (Build.VERSION.SDK_INT >= 21);
    mirror    = null;

    // navigation drawer
    navigationLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
    List<NavigationItem> navigationItems = new ArrayList<NavigationItem>();
    navigationAdapter = new NavigationAdapter(DroidPlayActivity.this, navigationItems);
    navigationList = (ListView) findViewById(R.id.drawer);
    navigationList.setAdapter(navigationAdapter);
    navigationList.setOnItemClickListener(DroidPlayActivity.this);
    updateNavigationItems();

    if (ExternalStorageUtils.has_permission(DroidPlayActivity.this))
      onPermissionGranted();
    else
      ExternalStorageUtils.request_permission(DroidPlayActivity.this);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case REQUEST_CODE_SCREEN_CAPTURE:
        if (resultCode != RESULT_OK) return;

        final MediaProjectionManager projectionManager = getProjectionManager();
        if (projectionManager == null) return;

        final MediaProjection mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) return;

        setMediaProjection(mediaProjection);

        Message msg = Message.obtain();
        msg.what = Constant.Msg.Msg_ScreenMirror_Stream_Start;
        MainApp.broadcastMessage(msg);
        break;
      case REQUEST_CODE_SETTINGS:
        PreferencesMgr.refresh();
        break;
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (ExternalStorageUtils.is_permission_granted(DroidPlayActivity.this, requestCode, grantResults))
      onPermissionGranted();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main_actions, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.settings : {
        startActivityForResult(SettingsActivity.getStartIntent(DroidPlayActivity.this), REQUEST_CODE_SETTINGS);
        break;
      }
      case R.id.exit : {
        Message msg = Message.obtain();
        msg.what = Constant.Msg.Msg_Exit_Service;
        MainApp.broadcastMessage(msg);
        break;
      }
    }
    return true;
  }

  @Override
  protected void onDestroy() {
    MainApp.unregisterHandler(DroidPlayActivity.class.getName());

    // save selected folder
    if ((adapter != null) && (adapter.getFolder() != null))
      PreferencesMgr.set_selected_folder(adapter.getFolder().getAbsolutePath());

    super.onDestroy();
  }

  // ---------------------------------------------------------------------------
  // FolderDialog.Callback

  @Override
  public void onFolderSelected(File folder) {
    adapter.setFolder(folder);
    updateFolder(folder.getAbsolutePath());
  }

  // ---------------------------------------------------------------------------
  // AdapterView.OnItemClickListener

  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    NavigationItem item = navigationAdapter.getItem(position);
    if (item == null) return;

    switch(item.getTag()) {
      case "connect": {
        Message msg = Message.obtain();
        msg.what = Constant.Msg.Msg_AirPlay_Show_Connect_Dialog;
        msg.obj  = (Context) DroidPlayActivity.this;
        MainApp.broadcastMessage(msg);
        break;
      }
      case "mirror": {
        mirror = ScreenMirrorMgr.getInstance(getApplicationContext());

        final MediaProjectionManager projectionManager = getProjectionManager();

        if (projectionManager != null)
          startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE);

        break;
      }
      case "pictures": {
        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        adapter.setFolder(folder);
        updateFolder(folder.getAbsolutePath());
        break;
      }
      case "videos": {
        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        adapter.setFolder(folder);
        updateFolder(folder.getAbsolutePath());
        break;
      }
      case "downloads": {
        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        adapter.setFolder(folder);
        updateFolder(folder.getAbsolutePath());
        break;
      }
      case "folders": {
        new FolderDialog(DroidPlayActivity.this, DroidPlayActivity.this, adapter.getFolder()).show();
        break;
      }
      case "stop": {
        Message msg = Message.obtain();
        msg.what = Constant.Msg.Msg_Stop;
        MainApp.broadcastMessage(msg);
        break;
      }
    }

    navigationLayout.closeDrawer(navigationList);
  }

  // ---------------------------------------------------------------------------
  // private

  private void startNetworkingService() {
    Intent intent = new Intent(getApplicationContext(), NetworkingService.class);
    MainApp.getInstance().startService(intent);
  }

  private void onPermissionGranted() {
    has_storage_permission = true;
    updateNavigationItems();

    // load selected folder
    File folder = new File(PreferencesMgr.get_selected_folder());

    // update folder label
    updateFolder(folder.getAbsolutePath());

    // the layout manager to display contents of selected folder
    grid = (GridView) findViewById(R.id.grid);
    grid.setEmptyView(findViewById(R.id.empty));
    grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        File file = (File) adapter.getItem(position);
        try {
          if (ExternalStorageUtils.isImageFile(file)) {
            Message msg = Message.obtain();
            msg.what = Constant.Msg.Msg_Photo;
            msg.obj  = file;
            MainApp.broadcastMessage(msg);
          }
          else if (ExternalStorageUtils.isVideoFile(file) || ExternalStorageUtils.isAudioFile(file)) {
            Message msg = Message.obtain();
            msg.what = Constant.Msg.Msg_Play;
            msg.obj  = file;
            MainApp.broadcastMessage(msg);
          }
          else {
            toast("Error: Unknown file type");
          }
        }
        catch (Exception e) {
          toast("Error: " + e.getMessage());
        }
      }
    });
    updateFolderLayout();

    startNetworkingService();
  }

  private void updateNavigationItems() {
    navigationAdapter.clear();
    navigationAdapter.add(    new NavigationItem("connect",   "Connect to AirPlay...", R.drawable.ic_cast_connected_grey600_36dp));

    if (has_airplay_connection) {
      if (canMirror)
        navigationAdapter.add(new NavigationItem("mirror",    "Mirror Screen",         R.drawable.ic_screen_mirror_grey600_36dp ));

      if (has_storage_permission) {
        navigationAdapter.add(new NavigationItem("pictures",  "Pictures",              R.drawable.ic_image_grey600_36dp         ));
        navigationAdapter.add(new NavigationItem("videos",    "Videos",                R.drawable.ic_videocam_grey600_36dp      ));
        navigationAdapter.add(new NavigationItem("downloads", "Downloads",             R.drawable.ic_file_download_grey600_36dp ));
        navigationAdapter.add(new NavigationItem("folders",   "Choose folder...",      R.drawable.ic_folder_grey600_36dp        ));
      }

      navigationAdapter.add(  new NavigationItem("stop",      "Stop playback",         R.drawable.ic_stop_grey600_36dp          ));
    }

    navigationAdapter.notifyDataSetChanged();
  }

  private void updateFolder(final String newFolder) {
    if (newFolder == null) return;

    handler.post(new Runnable() {
      @Override
      public void run() {
        TextView folder = (TextView) findViewById(R.id.folder);
        folder.setText(newFolder);
      }
    });

    PreferencesMgr.set_selected_folder(newFolder);
  }

  private void updateFolderLayout() {
    String folder_layout = PreferencesMgr.get_folder_layout();
    File folder = new File(PreferencesMgr.get_selected_folder());

    switch(folder_layout) {
      case "grid": {
        adapter = new FolderGridAdapter(DroidPlayActivity.this, folder);
        grid.setAdapter(null);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setAdapter(adapter);
        break;
      }
      case "list":
      default: {
        adapter = new FolderListAdapter(DroidPlayActivity.this, folder);
        grid.setAdapter(null);
        grid.setNumColumns(1);
        grid.setAdapter(adapter);
        break;
      }
    }
  }

  private void updateSubtitle() {
    subtitle(
      has_airplay_connection ? MainApp.receiverName : "Not connected"
    );
  }

  private void subtitle(final String message) {
    if (message == null) return;

    handler.post(new Runnable() {
      @Override
      public void run() {
        getActionBar().setSubtitle(message);
      }
    });
  }

  private void toast(final String message) {
    if (message == null) return;

    handler.post(new Runnable() {
      @Override
      public void run() {
        ToastUtils.showToast(DroidPlayActivity.this, message);
      }
    });
  }

  // ---------------------------------------------------------------------------
  // global Message listener

  private class DroidPlayHandler extends Handler {
    private WeakReference<DroidPlayActivity> activityRef;

    public DroidPlayHandler(DroidPlayActivity activity) {
      activityRef = new WeakReference<DroidPlayActivity>(activity);
    }

    @Override
    public void handleMessage(Message msg) {
      super.handleMessage(msg);

      DroidPlayActivity activity = activityRef.get();

      if (activity == null)
        return;
      if (activity.isFinishing())
        return;

      switch (msg.what) {
        case Constant.Msg.Msg_Change_Folder_Layout : {
          updateFolderLayout();
          break;
        }
        case Constant.Msg.Msg_AirPlay_Connect : {
          has_airplay_connection = true;
          MainApp.receiverName   = (String) msg.obj;
          updateSubtitle();
          updateNavigationItems();
          break;
        }
        case Constant.Msg.Msg_AirPlay_Disconnect : {
          has_airplay_connection = false;
          MainApp.receiverName   = null;
          updateSubtitle();
          updateNavigationItems();
          break;
        }
        case Constant.Msg.Msg_Exit_Service : {
          finish();
          break;
        }
      }

    }
  }

}
