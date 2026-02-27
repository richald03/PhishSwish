package com.example.phishswish.data;

import android.content.Context;

import com.example.phishswish.model.ScanEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanRepository {

    private final ScanDao scanDao;
    private final ExecutorService executorService;

    public ScanRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        scanDao = db.scanDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Inserts a scan record into the database on a background thread.
     */
    public void insertScan(ScanEntity scan) {
        executorService.execute(() -> scanDao.insert(scan));
    }

    /**
     * Fetches all scan records from the database. 
     * Note: This must be called from a background thread to avoid UI blocking.
     */
    public List<ScanEntity> fetchAllScans() {
        return scanDao.getAll();
    }

    /**
     * Deletes all scan records on a background thread.
     */
    public void deleteAllScans() {
        executorService.execute(scanDao::deleteAll);
    }
}
