
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

public class IndexerDbAdapter {

    // contains 2 tables, one has columns: URL, content, page_rank
    // the other has columns: word, URL, tf, word_tag

    // these are the column names
    public static final String COL_ID = "_id";
    public static final String COL_URL = "url";
    // content of the url after removing tags (used in phrase search)
    public static final String COL_CONTENT = "content";
    public static final String COL_PAGE_RANK = "page_rank";

    public static final String COL_WORD = "word";
    public static final String COL_SCORE = "score";

    public static final String COL_SRC_URL = "src_url";
    public static final String COL_DST_URL = "dst_url";

    // these are the corresponding indices
    public static final int INDEX_ID = 0;
    public static final int INDEX_URL = INDEX_ID + 1;
    public static final int INDEX_CONTENT = INDEX_ID + 2;
    public static final int INDEX_PAGE_RANK = INDEX_ID + 3;

    public static final int INDEX_WORD = INDEX_ID + 1;
    public static final int INDEX_URL_TABLE2 = INDEX_ID + 2;
    public static final int INDEX_TF = INDEX_ID + 3;
    public static final int INDEX_TF_IDF = INDEX_ID + 4;
    public static final int INDEX_WORD_TAG = INDEX_ID + 5;

    public static final int INDEX_SRC_URL = INDEX_ID + 1;
    public static final int INDEX_DST_URL = INDEX_ID + 2;

    private Connection conn;
    private static final String DATABASE_NAME = "dba_search_indexer";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "";
    private static final String CONNECTION_STRING = "jdbc:mysql://localhost:3306/";
    private static final String TABLE_URLS_NAME = "tb1_urls";
    private static final String TABLE_URLS_INDEX_NAME = "tbl_urls_index";
    private static final String TABLE_WORDS_NAME = "tb2_words";
    private static final String TABLE_WORDS_INDEX_NAME = "tb2_words_url_index";
    private static final String TABLE_LINKS_NAME = "tb3_links";

    // SQL statement used to create the database
    private static final String TABLE1_CREATE = String.format(
            "CREATE TABLE if not exists %s( %s INTEGER PRIMARY KEY AUTO_INCREMENT, %s varchar(256), %s TEXT, %s double);",
            TABLE_URLS_NAME, COL_ID, COL_URL, COL_CONTENT, COL_PAGE_RANK);

    private static final String TABLE1_INDEX_CREATE = String.format("CREATE UNIQUE INDEX if not exists %s ON %s(%s);",
            TABLE_URLS_INDEX_NAME, TABLE_URLS_NAME, COL_URL);

    private static final String TABLE2_CREATE = String.format(
            "CREATE TABLE if not exists %s(%s INTEGER PRIMARY KEY AUTO_INCREMENT,"
                    + " %s varchar(256), %s varchar(256), %s DOUBLE, FOREIGN KEY (%s) REFERENCES %s(%s) ON DELETE CASCADE);",
            TABLE_WORDS_NAME, COL_ID, COL_WORD, COL_URL, COL_SCORE, COL_URL, TABLE_URLS_NAME, COL_URL);

    private static final String TABLE2_INDEX_CREATE = String.format(
            "CREATE UNIQUE INDEX if not exists %s ON %s(%s, %s);", TABLE_WORDS_INDEX_NAME, TABLE_WORDS_NAME, COL_WORD,
            COL_URL);

    public static final String TABLE3_LINKS_CREATE = String.format(
            "CREATE TABLE IF NOT EXISTS %s( %s INTEGER PRIMARY KEY AUTO_INCREMENT,"
                    + " %s varchar(256), %s varchar(256), FOREIGN KEY (%s) REFERENCES %s(%s) ON DELETE CASCADE,"
                    + " FOREIGN KEY (%s) REFERENCES %s(%s) ON DELETE CASCADE, UNIQUE(%s, %s))",
            TABLE_LINKS_NAME, COL_ID, COL_SRC_URL, COL_DST_URL, COL_SRC_URL, TABLE_URLS_NAME, COL_URL, COL_DST_URL,
            TABLE_URLS_NAME, COL_URL, COL_SRC_URL, COL_DST_URL);

    private static final String DATABASE_CREATE = String.format("CREATE DATABASE IF NOT EXISTS %s;", DATABASE_NAME);

    public IndexerDbAdapter() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void createTables() {
        try (Statement stmt = conn.createStatement()) {
            stmt.addBatch(TABLE1_CREATE);
            stmt.addBatch(TABLE1_INDEX_CREATE);
            stmt.addBatch(TABLE2_CREATE);
            stmt.addBatch(TABLE2_INDEX_CREATE);
            stmt.addBatch(TABLE3_LINKS_CREATE);
            stmt.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void open() {
        try {
            
            conn = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD);
            
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(DATABASE_CREATE);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            conn.close();
            
            conn = DriverManager.getConnection(CONNECTION_STRING + DATABASE_NAME, USERNAME, PASSWORD);
            createTables();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (conn != null) {
            try {
                conn.close();
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public int getDocumentsNum() {

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE_URLS_NAME)) {
            if (rs.next())
                return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // content is the plain text of the url without tags
    public void addURL(String url, String content) {
        String sql = String.format(
                "INSERT INTO %s(%s, %s, %s) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE %s = VALUES(%s), %s = VALUES(%s)",
                TABLE_URLS_NAME, COL_URL, COL_CONTENT, COL_PAGE_RANK, COL_CONTENT, COL_CONTENT, COL_PAGE_RANK,
                COL_PAGE_RANK);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, url);
            ps.setString(2, content);
            ps.setDouble(3, 1.0 / (getDocumentsNum() + 1));
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getUnindexedURL() {
        String sql = String.format("SELECT %s FROM %s WHERE %s IS NULL LIMIT 1", COL_URL, TABLE_URLS_NAME, COL_CONTENT);
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next())
                return rs.getString(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void resetPagesRank() {
        String sql = String.format("UPDATE %s SET %s = %s", TABLE_URLS_NAME, COL_PAGE_RANK, 1.0 / getDocumentsNum());
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // score is sum of (term frequency of certain tag)*(tag score) of different html
    // tags of a word
    public void addWord(String word, String url, double score) {
        String sql = String.format("INSERT INTO %s(%s, %s, %s) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE %s = VALUES(%s)",
                TABLE_WORDS_NAME, COL_WORD, COL_URL, COL_SCORE, COL_SCORE, COL_SCORE);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, word);
            ps.setString(2, url);
            ps.setDouble(3, score);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    // return true when the link is added successfully, false otherwise
    public boolean addLink(String srcURL, String dstURL) {
        String sql = String.format("INSERT INTO %s(%s, %s) VALUES(?, ?)", TABLE_LINKS_NAME, COL_SRC_URL, COL_DST_URL);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, srcURL);
            ps.setString(2, dstURL);
            ps.executeUpdate();
        } catch (SQLException e) {
            return false;
        }
        return true;
    }

    // utility function to create placeholders (?) given a length
    private String makePlaceholders(int len) {
        if (len < 1) {
            // It will lead to an invalid query anyway ..
            throw new RuntimeException("No placeholders");
        } else {
            StringBuilder sb = new StringBuilder(len * 2 - 1);
            sb.append("?");
            for (int i = 1; i < len; i++) {
                sb.append(",?");
            }
            return sb.toString();
        }
    }

    // `page` is the page number in search result,
    // each page of search results contains `limit` urls
    public ArrayList<HashMap<String, String>> queryWords(String[] words, int limit, int page) {
        String sql = String.format("SELECT %s, %s FROM %s"
                + " JOIN (SELECT %s, log((select count(*) from %s)*1.0/count(%s)) as idf FROM %s GROUP BY %s)"
                + " as temp USING (%s) JOIN %s USING (%s) WHERE %s in (" + makePlaceholders(words.length)
                + ") and %s < 0.6 GROUP by %s ORDER BY SUM(%s*idf) DESC LIMIT ?, ?", COL_URL, COL_CONTENT,
                TABLE_WORDS_NAME, COL_WORD, TABLE_URLS_NAME, COL_URL, TABLE_WORDS_NAME, COL_WORD, COL_WORD,
                TABLE_URLS_NAME, COL_URL, COL_WORD, COL_SCORE, COL_URL, COL_SCORE);


        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < words.length; i++) {
                ps.setString(i + 1, words[i]);
            }
            ps.setInt(words.length + 1, (page - 1) * limit);
            ps.setInt(words.length + 2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                ArrayList<HashMap<String, String>> ret = new ArrayList<HashMap<String, String>>();
                while (rs.next()) {
                    HashMap<String, String> elem = new HashMap<String, String>();
                    elem.put("url", rs.getString(1));
                    elem.put("content", rs.getString(2));
                    ret.add(elem);
                }
                return ret;
            } catch (SQLException e) {
                e.printStackTrace();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public ArrayList<HashMap<String, String>> queryPhrase(String phrase, int limit, int page) {
        String sql = String.format("SELECT %s, %s FROM %s WHERE %s LIKE ? LIMIT ?, ?", COL_URL, COL_CONTENT, TABLE_URLS_NAME,
                COL_CONTENT);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            // escape wildcard characters in phrase query
            phrase = phrase.replace("_", "%_").replace("%", "%%");
            ps.setString(1, "%%" + phrase + "%%");
            ps.setInt(2, (page - 1) * limit);
            ps.setInt(3, limit);

            try (ResultSet rs = ps.executeQuery()) {
                ArrayList<HashMap<String, String>> ret = new ArrayList<HashMap<String, String>>();
                while (rs.next()) {
                    HashMap<String, String> elem = new HashMap<String, String>();
                    elem.put("url", rs.getString(1));
                    elem.put("content", rs.getString(2));
                    ret.add(elem);
                }
                return ret;
            } catch (SQLException e) {
                e.printStackTrace();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteURL(String url) {
        String sql = String.format("DELETE FROM %s WHERE %s = ?", TABLE_URLS_NAME, COL_URL);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, url);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public void deleteAllURLS() {
        String sql = String.format("DELETE FROM %s", TABLE_WORDS_NAME);
        try (Statement stmt = conn.createStatement()) {
            stmt.addBatch(sql);
            sql = String.format("DELETE FROM %s", TABLE_URLS_NAME);
            stmt.addBatch(sql);
            stmt.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

}