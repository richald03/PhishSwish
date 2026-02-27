package com.example.phishswish.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.phishswish.model.ScanEntity;

import java.util.List;

@Dao
public interface ScanDao {

    @Insert
    void insert(ScanEntity scan);

    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    List<ScanEntity> getAll();

    @Query("DELETE FROM scans")
    void deleteAll();
}
