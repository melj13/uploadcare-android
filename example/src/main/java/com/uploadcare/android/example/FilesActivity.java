package com.uploadcare.android.example;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.uploadcare.android.OrderDialogFragment;
import com.uploadcare.android.example.adapter.UploadcareFileAdapter;
import com.uploadcare.android.example.util.RecyclerViewOnScrollListener;
import com.uploadcare.android.library.api.FilesQueryBuilder;
import com.uploadcare.android.library.api.UploadcareClient;
import com.uploadcare.android.library.api.UploadcareFile;
import com.uploadcare.android.library.callbacks.UploadcareFilesCallback;
import com.uploadcare.android.library.exceptions.UploadcareApiException;
import com.uploadcare.android.library.urls.Order;

import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity with RecycleView dynamically populated with a list of Uploadcarefile files.
 */
public class FilesActivity extends AppCompatActivity
        implements UploadcareFileAdapter.ItemTapListener,
        View.OnClickListener, CheckBox.OnCheckedChangeListener,
        OrderDialogFragment.OrderSelectedListener {

    private final int ITEMS_PER_PAGE = 30;

    private final int FROM_DATE_PICK = 555;

    UploadcareClient client;

    RecyclerView mRecyclerView;

    private UploadcareFileAdapter mUploadcareFileAdapter;

    private LinearLayoutManager mLayoutManager;

    private RecyclerViewOnScrollListener mRecyclerViewOnScrollListener;

    private URI next = null;

    private View mRootView;

    private TextView mStatusTextView;

    private CheckBox storedCheckBox, removedCheckBox;

    private TextView dateFilterTextView;

    private TextView orderFilterTextView;

    private boolean filterStored = false;

    private boolean filterRemoved = false;

    private Date filterFromDate = null;

    private Order filterOrder = null;

    /**
     * Initialize variables and creates {@link UploadcareClient}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        setContentView(R.layout.activity_files);
        client = UploadcareClient.demoClient(); //new UploadcareClient("publickey", "privatekey"); Use your public and private keys from Uploadcare.com account dashboard.
        findViewById(R.id.btn_from).setOnClickListener(this);
        findViewById(R.id.btn_apply).setOnClickListener(this);
        findViewById(R.id.btn_order).setOnClickListener(this);
        mRootView = findViewById(R.id.root);
        mStatusTextView = findViewById(R.id.status_text);
        mRecyclerView = findViewById(R.id.recycler_view);
        storedCheckBox = findViewById(R.id.stored);
        removedCheckBox = findViewById(R.id.removed);
        dateFilterTextView = findViewById(R.id.date_filter);
        orderFilterTextView = findViewById(R.id.order_filter);
        mLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mUploadcareFileAdapter = new UploadcareFileAdapter(this, this);
        mRecyclerView.setAdapter(mUploadcareFileAdapter);
        mRecyclerViewOnScrollListener = new RecyclerViewOnScrollListener(mLayoutManager) {
            @Override
            public void onLoadMore(int current_page) {
                if (next != null) {
                    getFiles(next);
                }
            }
        };
        mRecyclerView.addOnScrollListener(mRecyclerViewOnScrollListener);

        storedCheckBox.setOnCheckedChangeListener(this);
        removedCheckBox.setOnCheckedChangeListener(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Launches {@link CdnActivity} when user clicks on item.
     *
     * @param uploadcareFile {@link UploadcareFile} which user clicked.
     */
    @Override
    public void itemTap(UploadcareFile uploadcareFile) {
        Intent intent = new Intent(this, CdnActivity.class);
        intent.putExtra("fileId", uploadcareFile.getFileId());
        startActivity(intent);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_from:
                showDateDialog(FROM_DATE_PICK);
                break;
            case R.id.btn_apply:
                getFiles(null);
                break;
            case R.id.btn_order:
                showOrderDialog();
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.stored:
                filterStored = isChecked;
                break;
            case R.id.removed:
                filterRemoved = isChecked;
                break;
        }
    }

    /**
     * Get Uploadcare files data with {@link UploadcareClient}.
     *
     * @param nextItems page offset. if {@code null} will fetch data without offset.
     */
    private void getFiles(final URI nextItems) {
        if (nextItems == null) {
            mUploadcareFileAdapter.clear();
            mRecyclerViewOnScrollListener.clear();
            showStatus(getString(R.string.activity_files_status_loading), false);
        }
        FilesQueryBuilder filesQueryBuilder = client.getFiles();
        if (filterStored) {
            filesQueryBuilder.stored(true);
        } else if (filterRemoved) {
            filesQueryBuilder.removed(true);
        }

        if (filterFromDate != null) {
            filesQueryBuilder.from(filterFromDate);
        }

        if (filterOrder != null) {
            filesQueryBuilder.ordering(filterOrder);
        }

        filesQueryBuilder.asListAsync(this, ITEMS_PER_PAGE, nextItems,
                new UploadcareFilesCallback() {
                    @Override
                    public void onFailure(UploadcareApiException e) {
                        hideStatus();
                        showErrorMessage(e.getLocalizedMessage());
                    }

                    @Override
                    public void onSuccess(List<UploadcareFile> files, URI next) {
                        hideStatus();
                        if (nextItems != null) {
                            mUploadcareFileAdapter.addFiles(files);
                        } else {
                            mUploadcareFileAdapter.updateFiles(files);
                            if (mUploadcareFileAdapter.isEmpty()) {
                                showStatus(getString(R.string.activity_files_no_items), true);
                            }
                        }
                        FilesActivity.this.next = next;
                    }
                });
    }

    /**
     * Shows status info when Adapter is empty
     *
     * @param message to show.
     * @param error   if set shows Retry button.
     */
    private void showStatus(String message, boolean error) {
        if (!error && !mUploadcareFileAdapter.isEmpty()) {
            hideStatus();
            return;
        }
        mStatusTextView.setText(message);
        mStatusTextView.setVisibility(View.VISIBLE);
    }

    /**
     * Hides status View
     */
    private void hideStatus() {
        mStatusTextView.setVisibility(View.GONE);
    }

    /**
     * Shows Snackbar with message if Adapter has data,
     * activates Status views if Adapter has no data.
     *
     * @param message to show.
     */
    private void showErrorMessage(String message) {
        if (!mUploadcareFileAdapter.isEmpty()) {
            hideStatus();
            Snackbar.make(mRootView, message, Snackbar.LENGTH_LONG).show();
        } else {
            showStatus(message, true);
        }
    }

    /**
     * Launches date picker dialog for filters.
     *
     * @param type type of filter to choose date for.
     */
    private void showDateDialog(final int type) {
        Calendar c = Calendar.getInstance();
        int mYear = c.get(Calendar.YEAR);
        int mMonth = c.get(Calendar.MONTH);
        int mDay = c.get(Calendar.DAY_OF_MONTH);
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, monthOfYear, dayOfMonth) -> {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    if (type == FROM_DATE_PICK) {
                        try {
                            filterFromDate = dateFormat
                                    .parse(year + "-" + (monthOfYear + 1) + "-" + dayOfMonth);
                            dateFilterTextView.setVisibility(View.VISIBLE);
                            dateFilterTextView.setText(getResources()
                                    .getString(R.string.activity_files_from_text,
                                            filterFromDate.toString()));
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                    }
                }, mYear, mMonth, mDay);
        dialog.show();
    }

    private void showOrderDialog() {
        OrderDialogFragment dialog = (OrderDialogFragment) getSupportFragmentManager()
                .findFragmentByTag(OrderDialogFragment.class.getSimpleName());

        if (dialog != null || getSupportFragmentManager().isStateSaved()) {
            return;
        }

        dialog = OrderDialogFragment.newInstance();
        dialog.show(getSupportFragmentManager(), OrderDialogFragment.class.getSimpleName());
    }

    @Override
    public void onOrderSelected(Order order) {
        filterOrder = order;

        orderFilterTextView.setVisibility(View.VISIBLE);
        orderFilterTextView.setText(getString(R.string.activity_files_order_text, order));
    }
}
