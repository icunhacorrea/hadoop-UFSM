// package Java
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.io.WritableComparable;

public class Ozone extends Configured implements Tool {

    public static void main(String[] args) throws Exception {
        int res = ToolRunner.run(new Configuration(), new Ozone(), args);
        System.exit(res);
    }

    @Override
    public int run(String[] args) throws Exception {

        // When implementing tool
        //Configuration conf = this.getConf();
        
        // Passing parameters to Hadoop : http://www.javatute.com/javatute/faces/post/hadoop/2014/passing-parameter-to-mapper-and-reducer-hadoop.xhtml
        
        Configuration conf =  new Configuration();
        conf.set("lulat",args[2]); // left-upper latitude
        conf.set("lulon",args[3]); // left-upper longitude
        conf.set("rllat",args[4]); // right-lower latitude
        conf.set("rllon",args[5]); // right-lower longitude

        //conf.set("mapreduce.input.fileinputformat.split.maxsize", args[7]);
        
        int nJobs = Integer.parseInt(args[6]);

        Job[] jobs = new Job[nJobs];
        
        
        for(int i = 0; i < nJobs; i++)
        {
            jobs[i] = new Job(conf, "Tool Job");
            jobs[i].setJarByClass(Ozone.class);
            jobs[i].setJobName("Ozone reader");
            FileInputFormat.addInputPath(jobs[i], new Path(args[0]));
            FileOutputFormat.setOutputPath(jobs[i], new Path(args[1] + Integer.toString(i)));
            jobs[i].setMapperClass(OzoneFilterMapper.class);
            //job.setCombinerClass(OzoneCombiner.class);
            jobs[i].setReducerClass(OzoneAvgReducer.class);
            jobs[i].setMapOutputKeyClass(FloatArrayWritable.class);
            jobs[i].setMapOutputValueClass(IntArrayWritable.class);
            jobs[i].setOutputKeyClass(Text.class);
            jobs[i].setOutputValueClass(Text.class);
            //jobs[i].setNumReduceTasks(Integer.parseInt(args[8]));
        }
        
        System.out.println("==============================================");
    	System.out.println("Quantidade de Jobs " + args[6] + " :");
        TimeWatch watch = new TimeWatch();
    	watch.start();
    	for(int i = 0; i < nJobs; i++)
    	{
            jobs[i].waitForCompletion(true);
            
            Configuration teste = jobs[i].getConfiguration();
            System.out.println("map tasks = " + teste.get("mapred.map.tasks"));
            System.out.println("reduce tasks = " + teste.get("mapred.reduce.tasks"));
    	}
		long passedTimeInMs = watch.time();
		System.out.println("TIME IN SEC: " + (passedTimeInMs / 1000.0));
    	System.out.println("==============================================");
    	return 0;
    }

    public static class OzoneAvgReducer extends Reducer<FloatArrayWritable, IntArrayWritable, Text, Text> {

        @Override
        public void reduce(FloatArrayWritable key, Iterable<IntArrayWritable> values, Context context) throws IOException, InterruptedException {
            int count = 0;
            float sum = 0;

            Writable[] coords = key.get();
            FloatWritable coordx = (FloatWritable) coords[0];
            FloatWritable coordy = (FloatWritable) coords[1];
            String latlong = Float.toString(coordx.get());
            latlong = latlong.concat("," + Float.toString(coordy.get()));
            Text newkey = new Text();
            newkey.set(latlong);

            //System.out.println("(" + coordx + "," + coordy + ")");

            for (IntArrayWritable value : values) {
                Writable[] vals = value.get();
                IntWritable intvals = (IntWritable) vals[3];
                int measure = intvals.get();
                if (measure > 0) {
                    sum += measure;
                    count += 1;
                }
            }

            float average = sum / count;

            Text newvalue = new Text();
            newvalue.set(Float.toString(average));
            context.write(newkey, newvalue);
        }
    }


    public static class OzoneFilterMapper extends Mapper<LongWritable, Text, FloatArrayWritable, IntArrayWritable> {

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

            Configuration conf = context.getConfiguration();
            String tmp = conf.get("lulat"); // left-upper latitude
            float rulat = Float.parseFloat(tmp);
            tmp = conf.get("lulon"); // left-upper longitude
            float rulon = Float.parseFloat(tmp);
            tmp = conf.get("rllat"); // right-lower latitude
            float lllat = Float.parseFloat(tmp);
            tmp = conf.get("rllon"); // right-lower longitude
            float lllon = Float.parseFloat(tmp);

            String line = value.toString();

            String[] tokens = line.split("\\s+");
            String linelat = tokens[3];
            // if the measure cover the latitudes between rulat and lllat
            if (Float.parseFloat(linelat) >= rulat && Float.parseFloat(linelat) <= lllat) {

                FloatWritable latitude = new FloatWritable(new Float(tokens[3]));
                //FloatArrayWritable mykey = new FloatArrayWritable();
                FloatWritable[] coords = new FloatWritable[2];
                coords[0] = latitude;


                IntWritable[] values = new IntWritable[4];

                IntWritable year = new IntWritable(new Integer(tokens[0]));
                values[0] = year;
                IntWritable month = new IntWritable(new Integer(tokens[1]));
                values[1] = month;
                IntWritable day = new IntWritable(new Integer(tokens[2]));
                values[2] = day;

                IntWritable mesure;
                FloatWritable longitude;
                float lon;
                // for each measure in the row, generates a <key,value> pair
                for (int i = 6; i < tokens.length; ++i) {
                    lon = Float.parseFloat(tokens[4]);
                    lon = lon + ((i - 6) * (Float.parseFloat(tokens[5]))); // Demorei mt pra entender.
                    // if the measure cover the longitudes between rolon and lllon
                    if (lon <= rulon && lon >= lllon) {
                        longitude = new FloatWritable(new Float(lon));
                        coords[1] = longitude;

                        mesure = new IntWritable(new Integer(tokens[i]));
                        values[3] = mesure;

                        FloatArrayWritable mykey = new FloatArrayWritable();
                        IntArrayWritable myvalue = new IntArrayWritable();

                        mykey.set(coords);
                        myvalue.set(values);

                        context.write(mykey, myvalue);
                    }
                }
            }
        }
    }


    public static class IntArrayWritable extends ArrayWritable {

        public IntArrayWritable() {
            super(IntWritable.class);
        }
    }

    public static class FloatArrayWritable extends ArrayWritable implements WritableComparable<FloatArrayWritable> {

        public FloatArrayWritable() {
            super(FloatWritable.class);
        }

        @Override
        public int compareTo(FloatArrayWritable o) {
            try {
                
                Writable[] self = this.get();
                Writable[] other = o.get();
                
                FloatWritable scoordx = (FloatWritable)self[0];
                FloatWritable scoordy = (FloatWritable)self[1];
                
                FloatWritable ocoordx = (FloatWritable)other[0];
                FloatWritable ocoordy = (FloatWritable)other[1];

                int cmp = scoordx.compareTo(ocoordx);
                if (cmp != 0) {
                    return cmp;
                }
                return scoordy.compareTo(ocoordy);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    public static class TimeWatch {
    	long starts;

	    public static TimeWatch start() {
	        return new TimeWatch();
	    }

	    private TimeWatch() {
	        reset();
	    }

	    public TimeWatch reset() {
	        starts = System.currentTimeMillis();
	        return this;
	    }

	    public long time() {
	        long ends = System.currentTimeMillis();
	        return ends - starts;
	    }

	    public long time(TimeUnit unit) {
	        return unit.convert(time(), TimeUnit.MILLISECONDS);
	    }
    }
}
