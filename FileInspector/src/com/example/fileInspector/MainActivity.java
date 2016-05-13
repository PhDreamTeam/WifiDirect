package com.example.fileInspector;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Scanner;


/**
 * The following aspects should be improved:
 * - show favorite in a list, manage them and enable go to Favorite
 */
public class MainActivity extends Activity {

    private static final String APP_MAIN_FILES_DIR_PATH = "/sdcard/Android/data/com.example.fileInspector";
    private static final String INTERNAL_STATE_FILENAME = "internalState.txt";
    public static final String TAG = "MainActivity";

    private TextView tvFSRoot;
    private ListView lvFSDirs;

    private FileFilter ffDirs = new FileFilter() {
        public boolean accept(File pathname) {
            return pathname.isDirectory();
        }
    };

    private FileFilter ffFiles = new FileFilter() {
        public boolean accept(File pathname) {
            return pathname.isFile();
        }
    };

    private Comparator<File> sortAscending = new Comparator<File>() {
        public int compare(File f1, File f2) {
            return f1.toString().compareTo(f2.toString());
        }
    };

    private ArrayList<FileGui> fileGuiContainer = new ArrayList<>();

    private ArrayList<String> favoriteFiles = new ArrayList<>();
    private ArrayList<String> favoriteDirectories = new ArrayList<>();

    private ArrayAdapter<String> fsDirsLVAdapter;
    private ListView lvFSFiles;
    private ArrayAdapter<String> fsFilesLVAdapter;
    private File currentDir = new File("/");
    private TextView tvFSPrevious;
    private Thread readFileThread;
    private LinearLayout llFSContents;
    private TextView tvFavoriteDir;

    /**
     * Called when the activity is first created.
     */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.d(TAG, "onCreate");

        tvFSRoot = (TextView) findViewById(R.id.tvFSRoot);
        tvFSPrevious = (TextView) findViewById(R.id.tvFSPrevious);
        tvFavoriteDir = (TextView) findViewById(R.id.tvFavoriteDir);

        lvFSDirs = (ListView) findViewById(R.id.lvFSDirs);
        lvFSFiles = (ListView) findViewById(R.id.lvFSFiles);
        llFSContents = (LinearLayout) findViewById(R.id.llFSContents);

        tvFSPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeToNewDir(currentDir.toString().equals("/") ? currentDir : currentDir.getParentFile());
            }
        });

        tvFavoriteDir.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (isFavoriteDir(currentDir.toString()))
                    removeFavoriteDir(currentDir.toString());
                else addFavoriteDir(currentDir.toString());
                setFavoriteDirGuiState();
            }
        });

        final ArrayList<String> dirsArrayList = new ArrayList<>();
        ArrayList<String> filesArrayList = new ArrayList<>();

        // Dirs list view adapter
        fsDirsLVAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dirsArrayList);
        lvFSDirs.setAdapter(fsDirsLVAdapter);

        // directories onClickListener
        AdapterView.OnItemClickListener dirClickListener = new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
                final String item = (String) parent.getItemAtPosition(position);
                view.animate().setDuration(200).alpha(0)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                changeToNewDir(new File(currentDir.toString() + "/" + item));
                                view.setAlpha(1);
                            }
                        });
            }
        };

        lvFSDirs.setOnItemClickListener(dirClickListener);


        // Files list view adapter
        fsFilesLVAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, filesArrayList);
        lvFSFiles.setAdapter(fsFilesLVAdapter);


        // directories onClickListener
        AdapterView.OnItemClickListener fsFilesLVAdapter = new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
                final String item = (String) parent.getItemAtPosition(position);
                view.animate().setDuration(200).alpha(0)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                processFile(new File("/" + currentDir.toString() + "/" + item));
                                view.setAlpha(1);
                            }
                        });
            }
        };

        lvFSFiles.setOnItemClickListener(fsFilesLVAdapter);

        // loading state from disk
        if (!loadStateFromDisk()) {
            // start showing root files
            showFiles(currentDir);
        }
    }

    /**
     * @param newDir
     */
    private void changeToNewDir(File newDir) {

        if (showFiles(newDir)) {
            tvFSRoot.setText(newDir.toString());
            currentDir = newDir;
            setFavoriteDirGuiState();
        }
    }

    public void setFavoriteDirGuiState() {
        tvFavoriteDir.setCompoundDrawablesWithIntrinsicBounds(isFavoriteDir(currentDir.toString()) ?
                android.R.drawable.star_big_on :
                android.R.drawable.star_big_off, 0, 0, 0); // presence_offline
    }

    /**
     *
     */
    private boolean showFiles(File path) {

        Log.d(TAG, "Show files: Path " + path.toString());


        // get directories and sort them
        File[] dirs = path.listFiles(ffDirs);
        if (dirs == null)
            return false;

        fsDirsLVAdapter.clear();
        fsFilesLVAdapter.clear();


        Log.d(TAG, "show files: SubDirs = " + Arrays.toString(dirs));
        Arrays.sort(dirs, sortAscending);

        // put dirs on list view
        for (File subDir : Arrays.asList(dirs)) {
            fsDirsLVAdapter.add(subDir.getName());
        }

        // get files and sort them
        File[] files = path.listFiles(ffFiles);
        Arrays.sort(files, sortAscending);

        // put dirs on list view
        for (File fileOnDir : Arrays.asList(files)) {
            fsFilesLVAdapter.add(fileOnDir.getName());
        }

        return true;
    }

    /**
     *
     */
    private void processFile(File file) {
        Log.d(TAG, "Processing file: " + file.toString());

        // build file gui from file
        FileGui fileGui = new FileGui(file, llFSContents, this);

        // keep file gui in global array
        synchronized (fileGuiContainer) {
            fileGuiContainer.add(fileGui);
        }

        // check if working thread is running if not create and start it
        if (readFileThread == null) {
            readFileThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        while (true) {
                            readContinuousUpdateFiles();
                            Thread.sleep(500);
                        }
                    } catch (InterruptedException e) {
                        Log.d(TAG, "ReadFileContinuously: Thread stopped...");
                    }
                }
            });
            readFileThread.start();
        }
    }

    /**
     *
     */
    private void readContinuousUpdateFiles() {
        synchronized (fileGuiContainer) {
            // check if no jobs, end thread
            if (fileGuiContainer.size() == 0) {
                readFileThread = null;
                Thread.currentThread().interrupt();
            }

            // do file read for every file continuously updated
            for (FileGui fileGui : fileGuiContainer) {
                if (fileGui.isContinuousUpdateFile()) {
                    readFileToTextView(fileGui.getFile(), fileGui.getTvFileContents());
                    continue;
                }
                if (!fileGui.isFileAlreadyShown()) {
                    readFileToTextView(fileGui.getFile(), fileGui.getTvFileContents());
                    fileGui.setFileAlreadyShown(true);
                }
            }
        }
    }


    /**
     *
     */
    private void readFileToTextView(final File file, final TextView tv) {
        try {
            Log.d(TAG, "Start reading file: " + file);
            final Scanner scan = new Scanner(file);
            StringBuilder fileContents = new StringBuilder();
            while (scan.hasNextLine()) {
                final String line = scan.nextLine();
                Log.d(TAG, "Read line from file: " + file + " -> " + line);
                fileContents.append(line).append("\n");
            }
            scan.close();
            final String finalContents = fileContents.toString();
            tv.post(new Runnable() {
                public void run() {
                    tv.setText(finalContents);
                }
            });
        } catch (final FileNotFoundException e) {
            tv.post(new Runnable() {
                public void run() {
                    tv.setText(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });

        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        saveStateToDisk();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    /**
     *
     */
    public void removeFileGui(FileGui fileGui) {
        // remove fileGui from global container
        synchronized (fileGuiContainer) {
            fileGuiContainer.remove(fileGui);
        }

        // remove gui view from app
        llFSContents.removeView(fileGui.getExternalLinearLayout());
    }

    /**
     *
     */
    private void saveStateToDisk() {
        // unsure path existence
        buildPath(MainActivity.APP_MAIN_FILES_DIR_PATH);

        File stateFile = new File(APP_MAIN_FILES_DIR_PATH, INTERNAL_STATE_FILENAME);

        Log.d(TAG, "Saving app state to file: " + stateFile.toString());

        PrintWriter pw = null;
        try {
            pw = new PrintWriter(stateFile);

            // write main path
            pw.println("mainPath  = " + tvFSRoot.getText().toString());

            // first keep favoriteDirs
            synchronized (favoriteDirectories) {
                for (String favorite : favoriteDirectories) {
                    pw.println("favoriteDir  = " + favorite);
                }
            }

            // first keep favoriteFiles (then open files)
            synchronized (favoriteFiles) {
                for (String favorite : favoriteFiles) {
                    pw.println("favoriteFile  = " + favorite);
                }
            }

            // write open files
            synchronized (fileGuiContainer) {
                for (FileGui fileGui : fileGuiContainer) {
                    pw.println("openFile  = " + fileGui.getFile().toString());
                }
            }


            pw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    /**
     *
     */
    private boolean loadStateFromDisk() {
        File stateFile = new File(APP_MAIN_FILES_DIR_PATH, INTERNAL_STATE_FILENAME);

        Log.d(TAG, "Loading app state from file: " + stateFile.toString());

        Scanner scan = null;
        try {
            scan = new Scanner(stateFile);

            // process lines
            while (scan.hasNextLine()) {
                String line = scan.nextLine().trim();
                if (line.length() == 0)
                    continue;

                Log.d(TAG, "Reading app state, read line: " + line);
                Scanner scanLine = new Scanner(line);
                String firstToken = scanLine.next();
                String secondToken = scanLine.next();
                String thirdToken = scanLine.next();
                scanLine.close();

                if (firstToken.equalsIgnoreCase("mainPath") && secondToken.equalsIgnoreCase("=")) {
                    currentDir = new File(thirdToken);
                    tvFSRoot.setText(currentDir.toString());
                    // start showing root files
                    showFiles(currentDir);
                }

                if (firstToken.equalsIgnoreCase("openFile") && secondToken.equalsIgnoreCase("=")) {
                    processFile(new File(thirdToken));
                }

                if (firstToken.equalsIgnoreCase("favoriteFile") && secondToken.equalsIgnoreCase("=")) {
                    addFavoriteFile(thirdToken);
                }

                if (firstToken.equalsIgnoreCase("favoriteDir") && secondToken.equalsIgnoreCase("=")) {
                    addFavoriteDir(thirdToken);
                }
            }

            setFavoriteDirGuiState();
            scan.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "Internal state file not found");
            return false;
        }
        return true;
    }


    /**
     * Creates the received path. The path has to be constructed segment by
     * segment because some segments could have dots and those segment must
     * be create separately from others
     */
    public static void buildPath(String path) {

        Log.d("Logger", "Building path: " + path);

        String[] components = path.split("/");

        String buildPath = "";
        for (String s : components) {
            if (s.equals("")) {
                if (buildPath.length() != 0)
                    throw new IllegalStateException("Error creating LOG directory: " + buildPath);
                buildPath += '/';
            } else {
                if (buildPath.charAt(buildPath.length() - 1) != '/')
                    buildPath += '/';
                buildPath += s;
                File logDir = new File(buildPath);
                if (!logDir.mkdir() && !logDir.exists())
                    throw new IllegalStateException("Error creating LOG directory: " + buildPath);
            }
        }
    }


    /**
     *
     */
    public boolean isFavoriteFile(String fileName) {
        return favoriteFiles.contains(fileName);
    }

    /**
     *
     */
    public void addFavoriteFile(String favorite) {
        favoriteFiles.add(favorite);
    }

    /**
     *
     */
    public void removeFavoriteFile(String fileName) {
        favoriteFiles.remove(fileName);
    }

    /**
     *
     */
    public boolean isFavoriteDir(String dirName) {
        return favoriteDirectories.contains(dirName);
    }

    /**
     *
     */
    public void addFavoriteDir(String dirName) {
        favoriteDirectories.add(dirName);
    }

    /**
     *
     */
    public void removeFavoriteDir(String dirName) {
        favoriteDirectories.remove(dirName);
    }
}

