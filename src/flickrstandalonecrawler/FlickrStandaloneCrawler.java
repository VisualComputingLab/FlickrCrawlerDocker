package flickrstandalonecrawler;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import com.mongodb.MongoOptions;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.util.JSON;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import javax.net.ssl.HttpsURLConnection;
import javax.xml.bind.DatatypeConverter;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 *
 * @author Michalis Lazaridis <michalis.lazaridis@iti.gr>
 */
public class FlickrStandaloneCrawler {
    
    private static final String API_SITE = "https://api.flickr.com/services/rest/?method=";
    private static final String PREFIX_PHOTOS = "flickr.photos.search";
    private static final String API_KEY = "&api_key=";
    private static final String PREFIX_TEXT = "&text=";
    private static final String PREFIX_MEDIA = "&media=";
    private static final String PREFIX_PER_PAGE = "&per_page=";
    private static final String PREFIX_PAGE = "&page=";
    private static final String PREFIX_FORMAT_JSON = "&format=json&nojsoncallback=1";
    private static final String PREFIX_EXTRAS = "&extras=owner_name%2Coriginal_format%2Curl_m%2Curl_n%2Curl_z%2Curl_c%2Curl_l%2Curl_o%2Cgeo";
    private static final String PREFIX_COMMENTS = "flickr.photos.comments.getList";
    private static final String PREFIX_PHOTO = "&photo_id=";
    
    private static int posts_downloaded = 0;
    
    private final String apiKey_val;
    private final String homeDirectory;
    private final String search_text;
    private final DBCollection col_records;
        
    public FlickrStandaloneCrawler(String apik, String text, String dlDir, DBCollection col_rec)
    {
        apiKey_val = apik;
        homeDirectory = dlDir;
        search_text = text;
        col_records = col_rec;
        
        File destinationPath = new File(homeDirectory);
        destinationPath.mkdirs();
        
        
    }

    private String saveImage(String imageUrl, String fileName) throws IOException
    {
        URL url = new URL(imageUrl);
        File destinationFile;
        OutputStream os;
        // build destination path
        try (InputStream is = url.openStream())
        {
            destinationFile = new File(fileName);
            if (!destinationFile.exists())
            {
                destinationFile.createNewFile();
            }   os = new FileOutputStream(destinationFile);
            byte[] b = new byte[2048];
            int length;
            while ((length = is.read(b)) != -1)
            {
                os.write(b, 0, length);
            }
        }
        os.close();
        
        posts_downloaded++;
        
        return destinationFile.getAbsolutePath();
    }
        
    public int run(int max_results)
    {        
        ArrayList<String> ids = new ArrayList<>();
        
        String basic_url = API_SITE + PREFIX_PHOTOS + API_KEY + apiKey_val;
        
        
        
        basic_url += PREFIX_TEXT + search_text;
        basic_url += PREFIX_MEDIA + "photos";
        basic_url += PREFIX_PER_PAGE + 500;
        basic_url += PREFIX_FORMAT_JSON;
        basic_url += PREFIX_EXTRAS;
        basic_url += PREFIX_PAGE;
        
        int page = 1;
        
        if (max_results > 4000) max_results = 4000;
        
        try
        {
            System.out.println("Started crawling flickr");
            
            do
            {
                URL the_url = new URL(basic_url + page);
                System.out.println("The request URL: " + the_url.toString());

                String rsp = callGET(the_url, null, null);

                JSONObject jobj = new JSONObject(rsp);
                //System.out.println("The object response: " + jobj);

                //Iterate for next pages of the response
                int pages = jobj.getJSONObject("photos").getInt("pages");

                JSONArray photoArr = jobj.getJSONObject("photos").getJSONArray("photo");

                for (int i = 0; i < photoArr.length(); i++)
                {
                    // get separate movie from main response
                    //JSONObject postResp = new JSONObject(photoArr.getString(i));
                    JSONObject postResp = photoArr.getJSONObject(i);
                    String id = postResp.getString("id");
                    
                    if (ids.contains(id))
                    {
                        System.out.println("Found old id " + id);
                        continue;
                    }
                    else
                    {
                        System.out.println("Found new id " + id);
                        ids.add(id);
                    }
                    
                    String owner_name = postResp.getString("ownername");
                    String photo_url;
                    
                    //if (postResp.has("url_o"))
                    //{
                    //    photo_url = postResp.getString("url_o");
                    //}
                    if (postResp.has("url_l"))
                    {
                        photo_url = postResp.getString("url_l");
                    }
                    else if (postResp.has("url_c"))
                    {
                        photo_url = postResp.getString("url_c");
                    }
                    else if (postResp.has("url_z"))
                    {
                        photo_url = postResp.getString("url_z");
                    }
                    else if (postResp.has("url_m"))
                    {
                        photo_url = postResp.getString("url_m");
                    }
                    else
                    {
                        continue;
                    }
                    
                    System.out.println("Trying to download " + photo_url);
                    String fileName = homeDirectory + "/" + "flickr_" + id + ".jpg";
                    saveImage(photo_url, fileName);
                    
                    JSONObject record = new JSONObject();
                    record.put("photo_id", id);
                    record.put("owner", owner_name);
                    record.put("path", fileName);
                    
                    if (col_records != null)
                    {
                        BasicDBObject doc = (BasicDBObject) JSON.parse(record.toString());
                        col_records.save(doc);
                    }
                    
                    if (posts_downloaded >= max_results) break;
                }

                if (page < pages)
                {
                    page++;
                }
                else
                {
                    break;
                }

            } while (posts_downloaded < max_results);
        }
        catch (IOException | JSONException e)
        {
            System.err.println("Exception while crawling: " + e);
        }
        
        System.out.println(posts_downloaded + " images downloaded");
        
        return posts_downloaded;
    }

    public String callGET(URL url, String usr, String pass)
    {
        String output = null;
        int code = 0;
        String servercredentials = null;
        
        if (usr != null && !usr.isEmpty() && pass != null && !pass.isEmpty())
            servercredentials = usr + ":" + pass;

        try 
        {
            HttpURLConnection httpCon;
            if (url.getPort() == 443 || url.getPort() == 8443)
                httpCon = (HttpsURLConnection) url.openConnection();
            else
                httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoOutput(true);
            //httpCon.setRequestProperty("Content-Type", "application/json");
            if (servercredentials != null)
                httpCon.setRequestProperty("Authorization", "Basic " + DatatypeConverter.printBase64Binary(servercredentials.getBytes()));  // for accessing rabbit rest api
            //else
            //    httpCon.setRequestProperty("Accept", "application/json");   // for accessing own services
            httpCon.setRequestMethod("GET");
            httpCon.setConnectTimeout(5000);
            httpCon.setReadTimeout(5000);
            
            output = convertStreamToString(httpCon.getInputStream());
            code = httpCon.getResponseCode();
        } 
        catch (IOException e)
        {
            System.err.println("IOException during GET: " + e + ", output: " + code);
            output = "{\"exception\":\"" + e + "\",\"code\":\"" + code + "\"}";
        }

        return output;
    }
    
    private static String convertStreamToString(InputStream is) throws IOException {
        //
        // To convert the InputStream to String we use the
        // Reader.read(char[] buffer) method. We iterate until the
        // Reader return -1 which means there's no more data to
        // read. We use the StringWriter class to produce the string.
        //
        if (is != null) {
            Writer writer = new StringWriter();

            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                is.close();
            }

            return writer.toString();
        } else {
            return "";
        }
    }
    
    private static JSONObject getJSONObjectFromFile(String filename) {
        
        String str = getJSONTextFromFile(filename);
        JSONObject json = null;
        
        try
        {
            json = new JSONObject(str);
        }
        catch (JSONException e)
        {
            System.err.println("JSONException during loading json object: " + e);
        }

        return json;
    }
    
    private static String getJSONTextFromFile(String filename)
    {
        StringBuilder sb = new StringBuilder();
        BufferedReader in = null;

        try {
            in = new BufferedReader(new FileReader(filename));
            String aLine;

            while ((aLine = in.readLine()) != null) {
                sb.append(aLine);
            }
        }
        catch (IOException e)
        {
            System.err.println("IOException during loading properties: " + e);
        }
        finally
        {
            if (in != null)
            {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
        }
        
        return sb.toString();
    }
    
//    public static void main(String[] args)
//    {
//        String apiKey = System.getenv("FLICKR_API_KEY");
//        String text = System.getenv("FLICKR_SEARCH_TEXT");
//        String dlDir = System.getenv("DOWNLOAD_LOCATION");
//        
//        text = text.replaceAll(",", "%2C").replaceAll(" ", "+");
//        
//        FlickrStandaloneCrawler fsc = new FlickrStandaloneCrawler(dlDir, apiKey, text);
//        
//        fsc.run();
//    }
    
    public static void main(String[] args) throws JSONException
    {
        String config_path = args[0];
        
        JSONObject properties = getJSONObjectFromFile(config_path);
        
        String apiKey = properties.getJSONObject("flickr").getString("api_key");
        String query = properties.getJSONObject("flickr").getString("query");
        int max_results = properties.getJSONObject("flickr").optInt("max_results", 10);
        String dlDir = properties.getString("output_hd");
        
        Mongo mongo = null;
        DBCollection col_records = null;
        
        if (properties.has("output_db"))
        {
            String host = properties.getJSONObject("output_db").getString("host");
            int port = properties.getJSONObject("output_db").optInt("port", 27017);
            String db = properties.getJSONObject("output_db").getString("db");
            String collection = properties.getJSONObject("output_db").getString("collection");
            String user = properties.getJSONObject("output_db").getString("user");
            String passwd = properties.getJSONObject("output_db").getString("password");
            
            try
            {
                ServerAddress serverAdr = new ServerAddress(host, port);
                MongoOptions options = new MongoOptions();
                options.connectionsPerHost = 10;

                mongo = new Mongo(serverAdr, options);
                mongo.setWriteConcern(WriteConcern.SAFE);
                DB db_se = mongo.getDB(db);
                db_se.authenticate(user, passwd.toCharArray());
                col_records = db_se.getCollection(collection);
            }
            catch (UnknownHostException ex) 
            {
                System.err.println("UnknownHostException during mongodb initialization: " + ex);
                return;
            }
        }
                
        query = query.replaceAll(",", "%2C").replaceAll(" ", "+");
        
        FlickrStandaloneCrawler fsc = new FlickrStandaloneCrawler(apiKey, query, dlDir, col_records);
        
        fsc.run(max_results);
        
        if (mongo != null)
            mongo.close();
    }
    
}
