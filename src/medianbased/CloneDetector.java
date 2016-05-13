package medianbased;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;

import models.Block;
import models.TokenShard;
import net.jmatrix.eproperties.EProperties;
import utility.Util;

public class CloneDetector {
    Map<Integer, Map<Integer, List<Float>>> shardedIndexMap;
    List<TokenShard> tokenShards;
    EProperties properties;
    public static Writer clonesWriter; // writer to write the output
    static FileInputStream fis;
    public static float threshold = 8.0f;
    public static float th = threshold * Util.MUL_FACTOR;

    public CloneDetector() throws FileNotFoundException {
        this.properties = new EProperties();
        this.readProperties();
        this.tokenShards = new ArrayList<TokenShard>();

    }

    public File getQueryDirectory() throws FileNotFoundException {
        File queryDir = new File(
                this.properties.getProperty("DATASET_DIR_PATH"));
        if (!queryDir.isDirectory()) {
            throw new FileNotFoundException("directory not found.");
        } else {
            System.out.println("Directory: " + queryDir.getName());
            return queryDir;
        }
    }

    private void search() throws FileNotFoundException {
        System.out.println("Begin Search");
        Util.createDirs(properties.getProperty("MEDIAN_OUTPUT_DIR"));
        File datasetDir = this.getQueryDirectory();
        int count = 0;
        if (datasetDir.isDirectory()) {
            System.out.println("Dataset Directory: "
                    + this.getQueryDirectory().getAbsolutePath());
            BufferedReader br = null;
            for (File inputFile : datasetDir.listFiles()) {
                String filename = inputFile.getName().replaceFirst("[.][^.]+$",
                        "");
                System.out.println("QUERY FILE is " + inputFile.getName());
                try {
                    CloneDetector.clonesWriter = Util
                            .openFile(
                                    properties.getProperty("MEDIAN_OUTPUT_DIR")
                                            + "/" + filename
                                            + "clones_index_WITH_FILTER.txt",
                                    false);
                    br = new BufferedReader(new InputStreamReader(
                            new FileInputStream(inputFile), "UTF-8"));
                    String line;
                    String text = null;
                    while ((line = br.readLine()) != null
                            && line.trim().length() > 0) {
                        Block query = this.getCandidateFromLine(line);
                        List<TokenShard> tokenShardsToSearch = getShardIdsForCandidate(query);
                        Block candidate = null;
                        if (query.project_id == 54 && query.file_id == 136) {
                            // System.out.println("query: " + query);
                        }
                        for (TokenShard shard : tokenShardsToSearch) {
                            int[] minmax = shard.getIndexRangeCandidates(
                                    query.minStdDev, query.maxStdDev);
                            if (query.project_id == 54 && query.file_id == 136) {
                                /*
                                 * System.out.println("min in shard: " +
                                 * minmax[0] + ", max in shard: " + minmax[1]);
                                 */
                            }
                            System.out.println(query + "\n NUM candidates: "+ (minmax[1]-minmax[0]));
                            /*
                             * System.out.println("NUM Candidates: " +
                             * (minmax[1] - minmax[0]) + ", query: " +
                             * query.project_id + "," + query.file_id +
                             * ", shard: " + shard.id + "," +
                             * "median and min max medians: " + query.median +
                             * "," + query.min_median + "," + query.max_median);
                             */
                            for (int i = minmax[0]; i <= minmax[1]; i++) {
                                candidate = shard.candidates.get(i);
                                if (candidate.project_id == 54
                                        && candidate.file_id == 137) {
                                    /*
                                     * System.out.println("candidate: " +
                                     * candidate);
                                     */
                                }
                                if ((candidate.file_id > query.file_id)) {
                                    if (candidate.numTokens >= query.minNumTokens
                                            && candidate.numTokens <= query.maxNumTokens) {
                                        if (candidate.uniqueTokens >= query.minUniqueTokens
                                                && candidate.uniqueTokens <= query.maxUniqueTokens) {
                                            if (candidate.numChars >= query.minNumChars
                                                    && candidate.numChars <= query.maxNumChars) {
                                                // if ((candidate.mad >=
                                                // query.minMad && candidate.mad
                                                // <= query.maxMad)
                                                // || (candidate.stdDev >=
                                                // query.minStdDev &&
                                                // candidate.stdDev <=
                                                // query.maxStdDev)) {
                                                text = query.project_id + ","
                                                        + query.file_id + ","
                                                        + candidate.project_id
                                                        + ","
                                                        + candidate.file_id;
                                                Util.writeToFile(
                                                        CloneDetector.clonesWriter,
                                                        text, true);
                                                // }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        count++;
                        System.out.println("lines processed: " + count);
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Exception caught: " + e.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private Block getCandidateFromLine(String line) {
        // TODO Auto-generated method stub
        String[] metaDataAndData = line.split("@#@");
        String metaDataStr = metaDataAndData[0];
        String[] metadataParts = metaDataStr.split(",");
        int projectId = Integer.parseInt(metadataParts[0]);
        int fileId = Integer.parseInt(metadataParts[1]);
        int numTokens = Integer.parseInt(metadataParts[2]);
        int uniqueTokens = Integer.parseInt(metadataParts[3]);
        int numCharacters = Integer.parseInt(metadataParts[4]);
        float median = Float.parseFloat(metadataParts[7]);
        float stdDev = Float.parseFloat(metadataParts[5]);
        float variance = 0;//Float.parseFloat(metadataParts[7]);
        float mad = Float.parseFloat(metadataParts[6]);
        Block candidate = new Block(median, projectId, fileId, numTokens,
                stdDev, variance, numCharacters, uniqueTokens, mad);
        return candidate;
    }

    private void prepare() throws IOException {

        this.setupShards();

        File datasetDir = this.getQueryDirectory();
        int count = 0;
        if (datasetDir.isDirectory()) {
            System.out.println("Dataset Directory: "
                    + this.getQueryDirectory().getAbsolutePath());
            BufferedReader br = null;
            for (File inputFile : datasetDir.listFiles()) {
                try {
                    br = new BufferedReader(new InputStreamReader(
                            new FileInputStream(inputFile), "UTF-8"));
                    String line;
                    while ((line = br.readLine()) != null
                            && line.trim().length() > 0) {
                        Block candidate = this.getCandidateFromLine(line);
                        List<TokenShard> tokenShardsToIndex = getShardIdsForCandidate(candidate);
                        for (TokenShard shard : tokenShardsToIndex) {
                            shard.candidates.add(candidate);
                        }
                        count++;
                        // System.out.println("lines indexed: " + count);
                    }
                } catch (Exception e) {
                    System.out.println("Exception caught: " + e.getMessage());
                }
            }
        }
        System.out.println("sorting dataset by std_devs");
        for (TokenShard shard : this.tokenShards) {
            System.out.println("sorting shard: " + shard.id);
            Collections.sort(shard.candidates);
        }
        System.out.println("sorting done!");
    }

    private void readProperties() throws FileNotFoundException {

        String propertiesPath = System.getProperty("properties.location");
        System.out.println("propertiesPath: " + propertiesPath);
        fis = new FileInputStream(propertiesPath);
        try {
            properties.load(fis);
        } catch (IOException e) {
            System.out.println("ERROR READING PROPERTIES FILE, "
                    + e.getMessage());
            System.exit(1);
        }
    }

    private void setupShards() throws IOException {

        boolean isSharding = Boolean.parseBoolean(properties
                .getProperty("IS_SHARDING"));
        int minTokens = Integer.parseInt(properties.getProperty("MIN_TOKENS"));
        int maxTokens = Integer.parseInt(properties.getProperty("MAX_TOKENS"));
        int shardId = 1;
        if (isSharding) {
            String shardSegment = properties
                    .getProperty("SHARD_MAX_NUM_TOKENS");
            String[] shardSegments = shardSegment.split(",");
            int max;
            for (String segment : shardSegments) {
                // create shards
                max = Integer.parseInt(segment);
                TokenShard shard = new TokenShard(shardId, minTokens, max);
                this.tokenShards.add(shard);
                minTokens = max + 1;
                shardId++;
            }
            // create the last shard
            TokenShard shard = new TokenShard(shardId, minTokens, maxTokens);
            this.tokenShards.add(shard);
        } else {
            TokenShard shard = new TokenShard(shardId, minTokens, maxTokens);
            this.tokenShards.add(shard);
        }
    }

    /*
     * private List<CharacterShard> getCharacterShardsForCharacterSize(long
     * size){ List<CharacterShard> shards = new ArrayList<CharacterShard>(); for
     * (CharacterShard shard : this.tokenShards){ if(shard.minChar <=size &&
     * shard.maxChar>=size){ shards.add(shard); } } return shards; }
     */

    public List<TokenShard> getShardIdsForCandidate(Block candidate) {
        List<TokenShard> shardsToReturn = new ArrayList<TokenShard>();
        for (TokenShard shard : this.tokenShards) {
            if (candidate.numTokens >= shard.minTokens
                    && candidate.numTokens <= shard.maxTokens) {
                shardsToReturn.add(shard);
            }
        }
        return shardsToReturn;
    }

    public void printDuration(long end_time, long start_time, String message) {
        Duration duration;
        try {
            duration = DatatypeFactory.newInstance().newDuration(
                    end_time - start_time);
            System.out.printf(message + ":  %02dh:%02dm:%02ds",
                    duration.getDays() * 24 + duration.getHours(),
                    duration.getMinutes(), duration.getSeconds());
            System.out.println();
        } catch (DatatypeConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        long time_start = System.currentTimeMillis();
        CloneDetector cd = new CloneDetector();
        System.out.println("PREPARING DATASTRUCTURES");
        long begin_time = System.currentTimeMillis();
        cd.prepare();
        long end_time = System.currentTimeMillis();
        cd.printDuration(end_time, begin_time, "preparation_time");
        System.out.println("PREPARING DATASTRUCTURES DONE!");
        begin_time = System.currentTimeMillis();
        cd.search();
        end_time = System.currentTimeMillis();
        cd.printDuration(end_time, begin_time, "search_time");
        System.out.println("search over!");
        if (null != fis) {
            fis.close();
        }
        Util.closeOutputFile(clonesWriter);
        long end_time_final = System.currentTimeMillis();
        cd.printDuration(end_time_final, time_start, "total_time");
    }
}
