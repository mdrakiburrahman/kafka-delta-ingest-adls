package com.mycompany.app;

// https://delta-io.github.io/connectors/latest/delta-standalone/api/java/io/delta/standalone/Snapshot.html
import io.delta.standalone.DeltaLog;
import io.delta.standalone.Snapshot;
import io.delta.standalone.data.CloseableIterator;
import io.delta.standalone.data.RowRecord;

import org.apache.hadoop.conf.Configuration;

public class App 
{
    public static void printSnapshotDetails(String title, Snapshot snapshot) {
        System.out.println("===== " + title + " =====");
        System.out.println("version: " + snapshot.getVersion());
        System.out.println("number data files: " + snapshot.getAllFiles().size());
        System.out.println("data files:");
        snapshot.getAllFiles().forEach(file -> System.out.println(file.getPath()));

        CloseableIterator<RowRecord> iter = snapshot.open();

        System.out.println("\ndata rows:");
        RowRecord row = null;
        int numRows = 0;
        while (iter.hasNext()) {
            row = iter.next();
            numRows++;

            Long c1 = row.isNullAt("c1") ? null : row.getLong("c1");
            Long c2 = row.isNullAt("c2") ? null : row.getLong("c2");
            String c3 = row.getString("c3");
            System.out.println(c1 + " " + c2 + " " + c3);
        }
        System.out.println("\nnumber rows: " + numRows);
        System.out.println("data schema:");
        System.out.println(row.getSchema().getTreeString());
        System.out.println("\n");
    }
    public static void main( String[] args )
    {
        System.out.println( "Reading pre-created Delta Files from: /tmp/delta_standalone_test" );

        DeltaLog log = DeltaLog.forTable(new Configuration(), "/tmp/delta_standalone_test/");

        printSnapshotDetails("current snapshot", log.snapshot());
        printSnapshotDetails("version 0 snapshot", log.getSnapshotForVersionAsOf(0));
        printSnapshotDetails("version 1 snapshot", log.getSnapshotForVersionAsOf(1));
        printSnapshotDetails("version 2 snapshot", log.getSnapshotForVersionAsOf(2));
    }
}
