package eu.sblendorio.bbs.tenants;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import droid64.addons.DiskUtilities;
import eu.sblendorio.bbs.core.HtmlUtils;
import eu.sblendorio.bbs.core.PetsciiThread;
import eu.sblendorio.bbs.core.XModem;

import org.apache.commons.text.WordUtils;

import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static eu.sblendorio.bbs.core.Colors.*;
import static eu.sblendorio.bbs.core.Keys.*;
import static eu.sblendorio.bbs.core.Utils.filterPrintable;
import static java.util.Collections.emptyMap;
import static org.apache.commons.collections4.MapUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.*;
import static org.apache.commons.lang3.math.NumberUtils.toInt;

public class CsdbLatestReleasesSD2IEC extends PetsciiThread {

    private static final String RSS = "https://csdb.dk/rss/latestreleases.php";

    private int currentPage = 1;
    protected int pageSize = 10;

    static class NewsFeed {
        final Date publishedDate;
        final String title;
        final String description;
        final String uri;

        NewsFeed(Date publishedDate, String title, String description, String downloadUri) {
            this.publishedDate = publishedDate; this.title = title; this.description = description; this.uri = downloadUri;
        }

        public String toString() {
            return "Title: "+title+"\nDate:"+publishedDate+"\nDescription:"+description+"\n";
        }
    }

    static class ReleaseEntry {
        final String id;
        final String releaseUri;
        final String type;
        final Date publishedDate;
        final String title;
        final String releasedBy;
        final List<String> links;

        ReleaseEntry(String id, String releaseUri, String type, Date publishedDate, String title, String releasedBy, List<String> links) {
            this.id = id; this.releaseUri = releaseUri; this.type = type;
            this.publishedDate = publishedDate; this.title = title; this.releasedBy = releasedBy; this.links = links;
        }

        public String toString() {
            return "Title: "+title+"\nDate:"+publishedDate+"\nreleasedBy:"+releasedBy+"\nlinks:"+links+"\n";
        }

    }

    private Map<Integer, ReleaseEntry> posts = emptyMap();

    @Override
    public void doLoop() throws Exception {
        listPosts();
        while (true) {
            log("CSDb waiting for input");
            write(WHITE);print("#"); write(GREY3);
            print(", [");
            write(WHITE); print("+-"); write(GREY3);
            print("]Page [");
            write(WHITE); print("H"); write(GREY3);
            print("]elp [");
            write(WHITE); print("R"); write(GREY3);
            print("]eload [");
            write(WHITE); print("."); write(GREY3);
            print("]");
            write(WHITE); print("Q"); write(GREY3);
            print("uit> ");
            resetInput();
            flush(); String inputRaw = readLine();
            String input = lowerCase(trim(inputRaw));
            if (".".equals(input) || "exit".equals(input) || "quit".equals(input) || "q".equals(input)) {
                break;
            } else if ("help".equals(input) || "h".equals(input)) {
                help();
                listPosts();
            } else if ("+".equals(input)) {
                ++currentPage;
                posts = null;
                try {
                    listPosts();
                } catch (NullPointerException e) {
                    --currentPage;
                    posts = null;
                    listPosts();
                }
            } else if ("-".equals(input) && currentPage > 1) {
                --currentPage;
                posts = null;
                listPosts();
            } else if ("--".equals(input) && currentPage > 1) {
                currentPage = 1;
                posts = null;
                listPosts();
            } else if ("r".equals(input) || "reload".equals(input) || "refresh".equals(input)) {
                posts = null;
                listPosts();
            } else if (posts.containsKey(toInt(input))) {
                displayPost(toInt(input));
                listPosts();
            } else if ("".equals(input)) {
                listPosts();
            }
        }
        flush();
    }

    private void displayPost(int n) throws Exception {
        int i = 3;
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        cls();
        logo();

        waitOn();
        final ReleaseEntry p = posts.get(n);
        String strDate;
        try {
            strDate = dateFormat.format(p.publishedDate);
        } catch (Exception e) {
            strDate = EMPTY;
        }
        final String releasedBy = p.releasedBy;
        final String url = p.links.get(0);
        final String title = p.title;
        final String type = p.type;
        final String id   = p.id;
        final String releaseUri = p.releaseUri;

        byte[] content = null;

        //Get Content File and get fileName
        DownloadData file = PetsciiThread.download(new URL(url));
        String fileName = file.getFilename();

        //Is D64
        if("D64".equals(fileName.substring(fileName.length()-3, fileName.length()).toUpperCase())) {
            logo();
            print("----------------------------------------");
            write(GREEN);
            print("       Download ");write(LIGHT_GREEN);print("D");write(GREEN);print("64 or ");write(LIGHT_GREEN);print("Z");write(GREEN);println("IP file?");
            write(LIGHT_GREEN);
            println("             Press D or Z");
            resetInput(); int key = readKey();
            key = Character.toLowerCase(key);

            if (key == 'd') {
                //Download D64
                content =  file.getContent();
            }
            else if (key == 'z')  {
                //Zip D64 into ZipFile
                content = DiskUtilities.zipBytes(fileName, file.getContent());
            }

        }
        //Is ZIP file
        else if (
                "ZIP".equals(fileName.substring(fileName.length()-3, fileName.length()).toUpperCase()) &&
                        !type.equals("Other Platform C64 Tool")
        ) {
            logo();
            print("----------------------------------------");
            write(GREEN);
            println("       After Download Zipfile...");
            println("         You can uncompress it ");
            content =  file.getContent();
        }
        // Type not Allowed
        else if (type.equals("Other Platform C64 Tool")) {
            content = null;
        }
        else {
            content = DiskUtilities.getPrgContentFromFile(file);
        }


        waitOff();

        newline();
        print("----------------------------------------");
        write(LIGHT_RED); print("Title:");

        write(PURPLE);println(title);
        write(LIGHT_RED); print("From: ");
        write(PURPLE); print(releasedBy);
        println();
        write(LIGHT_RED); print("Type: ");
        write(PURPLE); print(type);
        println();
        write(LIGHT_RED); print("ID:   ");
        write(PURPLE); print(id);
        println();
        write(LIGHT_RED); print("Date: ");
        write(PURPLE); println(strDate);
        if (content != null) {
            write(LIGHT_RED); print("Size: ");
            write(PURPLE); println(content.length + " bytes");
        }

        write(LIGHT_RED); print("File: ");
        write(PURPLE); println(fileName);

        write(LIGHT_RED); print("URL:  ");
        write(PURPLE); print(releaseUri);

        if (content == null) {
            log("Can't download " + releaseUri);
            write(RED, REVON); println("      ");
            write(RED, REVON); print(" WARN "); write(WHITE, REVOFF); println(" Can't handle this. Use browser.");
            write(RED, REVON); println("      "); write(WHITE, REVOFF);
            write(CYAN); println();
            print("SORRY - press any key to go back ");
            readKey();
            resetInput();
        } else {
            print("----------------------------------------");

            newline(); write(YELLOW);
            println("   Press any key to Prepare Download");
            println("       Press . to abort it");
            resetInput();
            int ch = readKey();
            if (ch == '.') return;
            println();
            cls();
            write(REVON, LIGHT_GREEN);
            write(REVON); println("                              ");
            write(REVON); println(" Please start XMODEM transfer ");
            write(REVON); println("                              ");
            write(REVOFF, WHITE);
            log("Downloading " + title + " - " + releaseUri);
            XModem xm = new XModem(cbm, cbm.out());
            xm.send(content);
            println();
            write(CYAN);
            print("DONE - press any key to go back ");
            readKey();
            resetInput();
        }
    }

    private void listPosts() throws Exception {
        cls();
        logo();
        if (isEmpty(posts)) {
            waitOn();
            posts = getPosts(currentPage, pageSize);
            waitOff();
        }
        for (Map.Entry<Integer, ReleaseEntry> entry: posts.entrySet()) {
            int i = entry.getKey();
            ReleaseEntry post = entry.getValue();
            write(WHITE); print(i + "."); write(GREY3);
            final int iLen = 37-String.valueOf(i).length();
            String title = post.title + " (" + post.releasedBy+")";
            String line = WordUtils.wrap(filterPrintable(HtmlUtils.htmlClean(title)), iLen, "\r", true);
            println(line.replaceAll("\r", "\r " + repeat(" ", 37-iLen)));
        }
        newline();
    }

    private static List<ReleaseEntry> getReleases(List<NewsFeed> entries) throws Exception {
        Pattern p = Pattern.compile("(?is)<a href=['\\\"]([^'\\\"]*?)['\\\"] title=['\\\"][^'\\\"]*?\\.(prg|zip|d64|d71|d81|d82)['\\\"]>");
        List<ReleaseEntry> list = new LinkedList<>();
        for (NewsFeed item: entries) {
            if (item.description.matches("(?is).*=\\s*[\\\"'][^\\\"']*\\.(prg|zip|d64|d71|d81|d82)[^\\\"']*[\\\"'].*")) {
                String releaseUri = item.uri;
                String id = item.uri.replaceAll("(?is).*id=([0-9a-zA-Z_\\-]+).*$", "$1"); // https://csdb.dk/release/?id=178862&rs
                String releasedBy = item.description.replaceAll("(?is).*Released by:\\s*[^>]*>(.*?)<.*", "$1");
                String type = item.description.replaceAll("(?is).*Type:\\s*[^>]*>(.*?)<.*", "$1");
                Matcher m = p.matcher(item.description);
                List<String> urls = new ArrayList<>();
                while (m.find()) urls.add(m.group(1));
                list.add(new ReleaseEntry(id, releaseUri, type, item.publishedDate, item.title, releasedBy, urls));
            }
        }
        return list;
    }


    private Map<Integer, ReleaseEntry> getPosts(int page, int perPage) throws Exception {
        if (page < 1 || perPage < 1) return null;

        List<NewsFeed> entries = getFeeds(RSS);
        List<ReleaseEntry> list = getReleases(entries);

        Map<Integer, ReleaseEntry> result = new LinkedHashMap<>();
        for (int i=(page-1)*perPage; i<page*perPage; ++i)
            if (i<list.size()) result.put(i+1, list.get(i));
        return result;
    }


    private static List<NewsFeed> getFeeds(String urlString) throws Exception {
        URL url = new URL(urlString);
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(url));
        List<CsdbLatestReleasesSD2IEC.NewsFeed> result = new LinkedList<>();
        List<SyndEntry> entries = feed.getEntries();
        for (SyndEntry e : entries)
            result.add(new CsdbLatestReleasesSD2IEC.NewsFeed(e.getPublishedDate(), e.getTitle(), e.getDescription().getValue(), e.getUri()));
        return result;
    }

    private void help() throws Exception {
        cls();
        logo();
        println();
        println();
        println("Press any key to go back");
        readKey();
    }

    private void logo() throws Exception {
        write(CLR, LOWERCASE, CASE_LOCK);
        write(LOGO);
        write(CYAN); gotoXY(14,2); print("Last Release for SD2IEC");
        write(GREY3); gotoXY(0,5);

    }

    private void waitOn() {
        print("PLEASE WAIT...");
        flush();
    }

    private void waitOff() {
        for (int i=0; i<14; ++i) write(DEL);
        flush();
    }

    private static final byte[] LOGO = new byte[] {
        32, 18, 5, -66, -69, -110, -69, 18, -66, -69, -110, -69, 18, 32, -69, -110,
        -69, 18, 32, -110, 13, 32, 18, 32, -110, -68, -66, 18, -69, -65, -110, -66,
        18, 32, -95, -110, -95, 18, 32, -69, -110, -69, 32, -102, -44, -56, -59, -96,
        -61, 45, 54, 52, 32, -45, -61, -59, -50, -59, 32, -60, -63, -44, -63, -62,
        -63, -45, -59, 13, 32, 18, 5, 32, -110, -84, -69, -94, 18, -69, -110, -69,
        18, 32, -95, -110, -95, 18, 32, -95, -110, -95, 13, 32, 18, -69, -66, -110,
        -66, 18, -69, -66, -110, -66, 18, 32, -66, -110, -66, 18, 32, -66, -110, -66, 13
    };
}
