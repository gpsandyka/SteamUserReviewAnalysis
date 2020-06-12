package org.bigdata;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import scala.Tuple2;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Hello world!
 */
public class App {
    private static final String CSGO_ID = "730";
    private static final String DOTA2_ID = "570";
    private static final String MnBII_ID = "261550";
    private static final String CHRONOARK_ID = "1188930";

    private static final Pattern SPACE = Pattern.compile(" ");
    private static final String STEAM_GAME_ID = MnBII_ID;

    public static void main(String[] args) throws IOException {

        //1. Untuk mengambil data User Review dari Steam
        List<String> input = fetchJSONSteamUserReview(STEAM_GAME_ID);
        if (input == null) {
            System.out.println("There's a problem on getting the User Review :(");
            return;
        }


        for (String temp : input) {
            //temp = temp.replaceAll("\\W+"," ");
            System.out.println(temp);
        }
        System.out.println(input.size());


        //Menyalakan mesin Spark
        SparkConf conf = new SparkConf().setAppName("startingSpark").setMaster("local[*]");
        JavaSparkContext sc = new JavaSparkContext(conf);

        //Mematikan logging yang tidak begitu penting (namun tidak bisa semuanya entah kenapa)
        Logger.getLogger("org.apache").setLevel(Level.WARN);

        //Membuat JavaRDD untuk SETIAP User Review agar tidak tercampur ketika melakukan distinct()
        ArrayList<JavaRDD<String>> arrayRDD = new ArrayList<>();
        for (String temp : input) {
            //2. Menghapus seluruh koma, titik, dan sebagainya (removeNonAlphabet())
            temp = temp.replaceAll("\\W+"," ").toLowerCase();

            List<String> temp2 = new ArrayList<>();
            temp2.add(temp);
            arrayRDD.add(sc.parallelize(temp2)
                    //3. Melakukan flatMap() agar memisahkan kata per kata dengan spasi sebagai pemecah
                    .flatMap(s -> Arrays.asList(SPACE.split(s)).iterator())
                    //5. Melakukan distinct() agar tidak ada kata yang duplikat
                    .distinct());
        }

        //Membuat JavaRDD baru yang akan menggabungkan semua JavaRDD sebelumnya pada array untuk dilakukan reduce
        JavaRDD<Tuple2<String,Integer>> emptyRDD = sc.emptyRDD();
        JavaPairRDD<String,Integer> outputRDD = JavaPairRDD.fromJavaRDD(emptyRDD);
        for (JavaRDD<String> temp : arrayRDD) {
            outputRDD = outputRDD.union(temp.mapToPair(s -> new Tuple2<>(s, 1)));
        }

        List<Tuple2<String, Integer>> output = outputRDD
                //6. Melakukan reduce() dengan menambahkan value apabila key sama
                .reduceByKey((i1, i2) -> i1 + i2)
                //7. Mensortir berdasarkan value
                .mapToPair(Tuple2::swap)
                .sortByKey()
                .mapToPair(Tuple2::swap)
                .collect();

        for (Tuple2<?, ?> tuple : output) {
            System.out.println(tuple._1() + ": " + tuple._2());
        }
        sc.close();


/*

        long linesCount = filteredLines.count();
        String firstLine = filteredLines.first();
        System.out.println("Total lines containing test : " + linesCount);
        System.out.println("First lines containing test : " + firstLine);

*/


        /*SparkSession spark = SparkSession
                .builder()
                .appName("Java_word_count")
                .master("local[*]") // Replace 4 with the number of cores on your processor
                .config("spark.sql.warehouse.dir", "file:///c:/tmp/spark-warehouse")
                .getOrCreate();

        Logger.getRootLogger().setLevel(Level.WARN);

        JavaRDD<String> lines = spark.INPUT_PATH.javaRDD();

        JavaRDD<String> words = lines.flatMap(s -> Arrays.asList(SPACE.split(s)).iterator());

        JavaPairRDD<String, Integer> ones = words.mapToPair(s -> new Tuple2<>(s, 1));

        JavaPairRDD<String, Integer> counts = ones.reduceByKey((i1, i2) -> i1 + i2);

        JavaPairRDD<Integer, String> swapped = counts.mapToPair(Tuple2::swap);
        swapped = swapped.sortByKey();

        List<Tuple2<String, Integer>> output = swapped.mapToPair(Tuple2::swap).collect();
        for (Tuple2<?, ?> tuple : output) {
            System.out.println(tuple._1() + ": " + tuple._2());
        }
        spark.stop();*/
    }

    public static List<String> fetchJSONSteamUserReview (String id) {
        String sURL = "https://store.steampowered.com/appreviews/" + id + "?json=1&language=english&day_range=14&review_type=negative&num_per_page=100";

        try {
            List<String> temp = new ArrayList<>();
            // Connect to the URL using java's native library
            URL url = new URL(sURL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            //add request header
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            int responseCode = con.getResponseCode();
            System.out.println("\nSending 'GET' request to URL : " + url);
            System.out.println("Response Code : " + responseCode);
            if(responseCode != 200) {throw new RuntimeException("HttpResponseCode: " +responseCode);}
            System.out.println("Request Success!! \n\n\n");

            StringBuilder inline = new StringBuilder();
            Scanner sc = new Scanner(url.openStream());
            while(sc.hasNext())
            {
                inline.append(sc.nextLine());
            }

            /*System.out.println("\nJSON data in string format");
            System.out.println(inline);*/

            sc.close();

            JSONParser parse = new JSONParser();
            JSONObject jobj = (JSONObject)parse.parse(String.valueOf(inline));
            JSONArray jsonarr_1 = (JSONArray) jobj.get("reviews");

            //Get data for Results array
            for(int i=0;i<jsonarr_1.size();i++)
            {
                //Store the JSON objects in an array
                //Get the index of the JSON object and print the values as per the index
                JSONObject jsonobj_1 = (JSONObject)jsonarr_1.get(i);
                /*System.out.println("Elements under results array");
                System.out.println("\nLanguage: " +jsonobj_1.get("language"));
                System.out.println("Positive?: " +jsonobj_1.get("voted_up"));
                System.out.println("Text: " +jsonobj_1.get("review"));*/
                temp.add((String) jsonobj_1.get("review"));
            }
            return temp;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}