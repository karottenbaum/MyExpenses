package org.totschnig.myexpenses.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.Toast;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment;
import org.totschnig.myexpenses.dialog.SelectUnSyncedAccountDialogFragment;
import org.totschnig.myexpenses.fragment.SyncBackendList;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.model.Model;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.sync.ServiceLoader;
import org.totschnig.myexpenses.sync.SyncBackendProviderFactory;
import org.totschnig.myexpenses.util.Result;
import org.totschnig.myexpenses.util.UiUtils;

import java.io.Serializable;
import java.util.List;

import static org.totschnig.myexpenses.task.TaskExecutionFragment.TASK_CREATE_SYNC_ACCOUNT;
import static org.totschnig.myexpenses.task.TaskExecutionFragment.TASK_REPAIR_SYNC_BACKEND;
import static org.totschnig.myexpenses.task.TaskExecutionFragment.TASK_SYNC_LINK_LOCAL;
import static org.totschnig.myexpenses.task.TaskExecutionFragment.TASK_SYNC_LINK_REMOTE;
import static org.totschnig.myexpenses.task.TaskExecutionFragment.TASK_SYNC_LINK_SAVE;
import static org.totschnig.myexpenses.task.TaskExecutionFragment.TASK_SYNC_REMOVE_BACKEND;
import static org.totschnig.myexpenses.task.TaskExecutionFragment.TASK_SYNC_UNLINK;

public class ManageSyncBackends extends SyncBackendSetupActivity implements ContribIFace {

  private static final int REQUEST_REPAIR_INTENT= 1;

  private static final String KEY_PACKED_POSITION = "packedPosition";
  private Account newAccount;

  private List<SyncBackendProviderFactory> backendProviders;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    backendProviders = ServiceLoader.load(this);
    setTheme(MyApplication.getThemeIdEditDialog());
    super.onCreate(savedInstanceState);
    setContentView(R.layout.manage_sync_backends);
    setupToolbar(true);
    setTitle(R.string.pref_manage_sync_backends_title);
    if (savedInstanceState == null) {
      if (!ContribFeature.SYNCHRONIZATION.isAvailable()) {
        contribFeatureRequested(ContribFeature.SYNCHRONIZATION, null);
      }
      sanityCheck();
    }
  }

  private void sanityCheck() {
    for (SyncBackendProviderFactory factory: backendProviders) {
      Intent repairIntent = factory.getRepairIntent(this);
      if (repairIntent != null) {
        startActivityForResult(repairIntent, REQUEST_REPAIR_INTENT);
        //for the moment we handle only one problem at one time
        break;
      }
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.sync_backend, menu);
    addSyncProviderMenuEntries(menu.findItem(R.id.CREATE_COMMAND).getSubMenu(), backendProviders);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (getSyncBackendProviderFactoryById(backendProviders, item.getItemId()) != null) {
      contribFeatureRequested(ContribFeature.SYNCHRONIZATION, item.getItemId());
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onPositive(Bundle args) {
    switch (args.getInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE)) {
      case R.id.SYNC_UNLINK_COMMAND: {
        startTaskExecution(TASK_SYNC_UNLINK,
            new String[]{args.getString(DatabaseConstants.KEY_UUID)}, null, 0);
        return;
      }
      case R.id.SYNC_REMOVE_BACKEND_COMMAND: {
        startTaskExecution(TASK_SYNC_REMOVE_BACKEND,
            new String[]{args.getString(DatabaseConstants.KEY_SYNC_ACCOUNT_NAME)}, null, 0);
        return;
      }
      case R.id.SYNC_LINK_COMMAND_LOCAL_DO: {
        Account account = getListFragment().getAccountForSync(args.getLong(KEY_PACKED_POSITION));
        startTaskExecution(TASK_SYNC_LINK_LOCAL,
            new String[]{account.uuid}, account.getSyncAccountName(), 0);
        return;
      }
      case R.id.SYNC_LINK_COMMAND_REMOTE_DO: {
        Account account = getListFragment().getAccountForSync(args.getLong(KEY_PACKED_POSITION));
        startTaskExecution(TASK_SYNC_LINK_REMOTE,
            null, account, 0);
        return;
      }
    }
    super.onPositive(args);
  }

  @Override
  public boolean dispatchCommand(int command, Object tag) {
    switch (command) {
      case R.id.SYNC_LINK_COMMAND_LOCAL: {
        Bundle b = new Bundle();
        b.putString(ConfirmationDialogFragment.KEY_MESSAGE,
            getString(R.string.dialog_confirm_sync_link_local));
        b.putInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE, R.id.SYNC_LINK_COMMAND_LOCAL_DO);
        b.putInt(ConfirmationDialogFragment.KEY_POSITIVE_BUTTON_LABEL, R.string.dialog_command_sync_link_local);
        b.putInt(ConfirmationDialogFragment.KEY_NEGATIVE_BUTTON_LABEL, android.R.string.cancel);
        b.putLong(KEY_PACKED_POSITION, (Long) tag);
        ConfirmationDialogFragment.newInstance(b).show(getSupportFragmentManager(), "SYNC_LINK_LOCAL");
        break;
      }
      case R.id.SYNC_LINK_COMMAND_REMOTE: {
        Bundle b = new Bundle();
        b.putString(ConfirmationDialogFragment.KEY_MESSAGE,
            getString(R.string.dialog_confirm_sync_link_remote));
        b.putInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE, R.id.SYNC_LINK_COMMAND_REMOTE_DO);
        b.putInt(ConfirmationDialogFragment.KEY_POSITIVE_BUTTON_LABEL, R.string.dialog_command_sync_link_remote);
        b.putInt(ConfirmationDialogFragment.KEY_NEGATIVE_BUTTON_LABEL, android.R.string.cancel);
        b.putLong(KEY_PACKED_POSITION, (Long) tag);
        ConfirmationDialogFragment.newInstance(b).show(getSupportFragmentManager(), "SYNC_LINK_REMOTE");
        break;
      }
      case R.id.TRY_AGAIN_COMMAND: {
        sanityCheck();
      }
    }
    return super.dispatchCommand(command, tag);
  }

  @Override
  public void onPostExecute(int taskId, Object o) {
    super.onPostExecute(taskId, o);
    Result result = (Result) o;
    switch (taskId) {
      case TASK_CREATE_SYNC_ACCOUNT: {
        if (result.success) {
          getListFragment().reloadAccountList();
          if (((Integer) result.extra[1]) > 0) {
            showSelectUnsyncedAccount((String) result.extra[0]);
          }
        }
        break;
      }
      case TASK_SYNC_REMOVE_BACKEND: {
        if (result.success) {
          getListFragment().reloadAccountList();
        }
        break;
      }
      case TASK_SYNC_LINK_SAVE: {
        Toast.makeText(this, result.print(this), Toast.LENGTH_LONG).show();
        //fall through
      }
      case TASK_SYNC_UNLINK:
      case TASK_SYNC_LINK_LOCAL:
      case TASK_SYNC_LINK_REMOTE: {
        if (result.success) {
          getListFragment().reloadLocalAccountInfo();
        }
        break;
      }
      case TASK_REPAIR_SYNC_BACKEND: {
        String resultPrintable = result.print(this);
        if (resultPrintable != null) {
          if (result.success) {
            Snackbar snackbar = Snackbar.make(
                findViewById(R.id.container), resultPrintable, Snackbar.LENGTH_LONG);
            UiUtils.configureSnackbarForDarkTheme(snackbar);
            snackbar.show();
          } else {
            Bundle b = new Bundle();
            b.putString(ConfirmationDialogFragment.KEY_MESSAGE, resultPrintable);
            b.putInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE, R.id.TRY_AGAIN_COMMAND);
            b.putInt(ConfirmationDialogFragment.KEY_POSITIVE_BUTTON_LABEL, R.string.button_label_try_again);
            ConfirmationDialogFragment.newInstance(b).show(getSupportFragmentManager(), "REPAIR_SYNC_FAILURE");
          }
        }
      }
    }
  }

  protected void showSelectUnsyncedAccount(String accountName) {
    //if we were called from AccountEdit, we do not show the unsynced account selection
    //since we suppose that user wants to create one account for the account he is editing
    if (getCallingActivity() == null) {
      SelectUnSyncedAccountDialogFragment.newInstance(accountName)
          .show(getSupportFragmentManager(), "SELECT_UNSYNCED");
    }
  }

  private SyncBackendList getListFragment() {
    return (SyncBackendList) getSupportFragmentManager().findFragmentById(R.id.backend_list);
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.SYNC_DOWNLOAD_COMMAND:
        if (PrefKey.NEW_ACCOUNT_ENABLED.getBoolean(true)) {
          newAccount = getListFragment().getAccountForSync(
              ((ExpandableListContextMenuInfo) item.getMenuInfo()).packedPosition);
          startDbWriteTask(false);
        } else {
          contribFeatureRequested(ContribFeature.ACCOUNTS_UNLIMITED, null);
        }
        return true;
    }
    return super.onContextItemSelected(item);
  }

  @Override
  public Model getObject() {
    return newAccount;
  }

  @Override
  public void contribFeatureCalled(ContribFeature feature, Serializable tag) {
    if (tag instanceof Integer) {
      SyncBackendProviderFactory syncBackendProviderFactory =
          getSyncBackendProviderFactoryById(backendProviders, (Integer) tag);
      if (syncBackendProviderFactory != null) {
        syncBackendProviderFactory.startSetup(this);
      }
    }
  }

  @Override
  public void contribFeatureNotCalled(ContribFeature feature) {

  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_REPAIR_INTENT && resultCode == RESULT_OK) {
      for (SyncBackendProviderFactory factory: backendProviders) {
        if (factory.startRepairTask(this, data)) {
          break;
        }
      }
    }
  }
}
