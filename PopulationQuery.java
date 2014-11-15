
//import com.sun.org.apache.bcel.internal.generic.FLOAD;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.Float;
import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class PopulationQuery {
	// next four constants are relevant to parsing
	public static final int TOKENS_PER_LINE  = 7;
	public static final int POPULATION_INDEX = 4; // zero-based indices
	public static final int LATITUDE_INDEX   = 5;
	public static final int LONGITUDE_INDEX  = 6;
	
	// TODO: get rid of later and rearrange code
	public static float minLat;
	public static float maxLat;
	public static float minLong;
	public static float maxLong;
	
	public static int totalPop = 0;

    static final ForkJoinPool fjPool = new ForkJoinPool();
	
	// parse the input file into a large array held in a CensusData object
	public static CensusData parse(String filename) {
		CensusData result = new CensusData();
		
        try {
            BufferedReader fileIn = new BufferedReader(new FileReader(filename));
            
            // Skip the first line of the file
            // After that each line has 7 comma-separated numbers (see constants above)
            // We want to skip the first 4, the 5th is the population (an int)
            // and the 6th and 7th are latitude and longitude (floats)
            // If the population is 0, then the line has latitude and longitude of +.,-.
            // which cannot be parsed as floats, so that's a special case
            //   (we could fix this, but noisy data is a fact of life, more fun
            //    to process the real data as provided by the government)
            
            String oneLine = fileIn.readLine(); // skip the first line

            // read each subsequent line and add relevant data to a big array
            while ((oneLine = fileIn.readLine()) != null) {
                String[] tokens = oneLine.split(",");
                if(tokens.length != TOKENS_PER_LINE)
                	throw new NumberFormatException();
                int population = Integer.parseInt(tokens[POPULATION_INDEX]);
                if(population != 0)
                	result.add(population,
                			   Float.parseFloat(tokens[LATITUDE_INDEX]),
                		       Float.parseFloat(tokens[LONGITUDE_INDEX]));
            }

            fileIn.close();
        } catch(IOException ioe) {
            System.err.println("Error opening/reading/writing input or output file.");
            System.exit(1);
        } catch(NumberFormatException nfe) {
            System.err.println(nfe.toString());
            System.err.println("Error in file format");
            System.exit(1);
        }
        return result;
	}


	// argument 1: file name for input data: pass this to parse
	// argument 2: number of x-dimension buckets
	// argument 3: number of y-dimension buckets
	// argument 4: -v1, -v2, -v3, -v4, or -v5
	public static void main(String[] args) throws IllegalArgumentException {

        if (args.length < 4) {
            throw new IllegalArgumentException();
        }

        String filename = args[0];
        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        String version = args[3];

        CensusData censusData = parse(filename);

        Scanner console = new Scanner(System.in);
        double[] sizes = new double[2];
        int[][] grid = new int[x][y];

        if(version.equals("-v1")) {
            sizes = versionOneDivide(censusData, x, y);
            System.out.println("sizes 0 " + sizes[0]); //TODO: BUG: CURRENTLY DIF FOR V1 AND V2
            System.out.println("sizes 0 " + sizes[1]);

        } else if(version.equals("-v2")) {
            double[] corners = versionTwoDivide(censusData);
            minLat = (float)corners[0];
            maxLat = (float)corners[1];
            minLong = (float)corners[2];
            maxLong = (float)corners[3];
            totalPop = (int)corners[4];

            sizes[0] = (corners[3] - corners[2]) / x; //TODO: BUG: CURRENTLY DIF FOR V1 AND V2
            sizes[1] = (corners[1] - corners[0]) / y;
            System.out.println("sizes 0: " + sizes[0]);
            System.out.println("sizes 1: " + sizes[1]);

        } else if(version.equals("-v3")) {
            sizes = versionOneDivide(censusData, x, y);
            grid = versionThreeDivide(censusData, x, y, sizes);
        } else {

        }

        boolean cont = true;
        while (cont) {
            System.out.println("Please give west, south, east, north coordinates of your query");
            System.out.println("  rectangle:");
            String rect = console.next();

            String[] borders = rect.trim().split("-"); //todo: change

            System.out.println("hey borders has a length of " + borders.length); //todo: remove

            if (borders.length == 4) {
                int west = Integer.parseInt(borders[0]);
                int south = Integer.parseInt(borders[1]);
                int east = Integer.parseInt(borders[2]);
                int north = Integer.parseInt(borders[3]);

                if ((west < 1) || (west > x) || (south < 1) || (south > y)
                        || (east < west) || (east > x) || (north < south) || (north > y)) {
                    throw new IllegalArgumentException();
                }

                if (version.equals("-v1")) {
                    int queryPop = versionOneQuery(censusData, sizes, west, south, east, north);

                    double percent = (double)queryPop / totalPop;
                    percent = Math.round(percent * 100 * 100);
                    percent = percent / 100;
                    String perc = String.format("%.2f", percent);

                    System.out.println("population of rectangle: " + queryPop); //TODO: BUG ON QUERY 1
                    System.out.println("total pop: " + totalPop); //TODO: remove
                    System.out.println("percent of total population: " + perc);

                } else if(version.equals("-v2")) {
                    int[] border = {west, south, east, north};
                    int queryPop = versionTwoQuery(censusData, sizes, border);

                    double percent = (double)queryPop / totalPop;
                    percent = Math.round(percent * 100 * 100);
                    percent = percent / 100;
                    String perc = String.format("%.2f", percent);

                    System.out.println("population of rectangle: " + queryPop); //TOOD: BUG ON QUERY 2
                    System.out.println("total pop: " + totalPop); //TODO: remove
                    System.out.println("percent of total population: " + perc);

                } else if(version.equals("-v3")) {
                    int queryPop = versionThreeQuery(grid, west, south, east, north);

                    double percent = (double)queryPop / totalPop;
                    percent = Math.round(percent * 100 * 100);
                    percent = percent / 100;
                    String perc = String.format("%.2f", percent);

                    System.out.println("population of rectangle: " + queryPop);
                    System.out.println("total pop: " + totalPop); //TODO: remove
                    System.out.println("percent of total population: " + perc);
                } else {
                    //TODO: -v4
                }
            } else {
                cont = false;
            }
        }
	}

	public static double[] versionOneDivide(CensusData censusData, int x, int y) {
		minLat = censusData.data[0].latitude;
		maxLat = censusData.data[0].latitude;
		minLong = censusData.data[0].longitude;
		maxLong = censusData.data[0].longitude;
        totalPop = censusData.data[0].population;
		
		// compute US rectangle size and total population
		for (int i = 1; i < censusData.data_size; i++) {
			float lat = censusData.data[i].latitude;
			float lon = censusData.data[i].longitude;
			minLat = Math.min(minLat, lat);
			maxLat = Math.max(maxLat, lat);
			minLong = Math.min(minLong, lon);
			maxLong = Math.max(maxLong, lon);
            totalPop += censusData.data[i].population;
		}


        double colSize = ((double)maxLong - (double)minLong) / x;
        double rowSize = ((double)maxLat - (double)minLat) / y;
        double[] sizes = {colSize, rowSize};
        return sizes;
				
	}

    public static int versionOneQuery(CensusData censusData, double[] sizes,
                                       int west, int south, int east, int north) {
        int queryPop = 0;

        for (int i = 0; i < censusData.data_size; i++) {
            double curX = (censusData.data[i].longitude - minLong) / sizes[0]; //colSize
            double curY = (censusData.data[i].latitude - minLat) / sizes[1]; //rowSize
            int column = (int)curX + 1;
            int row = (int)curY + 1;

            if ((column >= west) && (column <= east) && (row >= south) && (row <= north)) {
                queryPop += censusData.data[i].population;
            }
        }
        return queryPop;
    }



    static class VTwoDivide extends RecursiveTask<double[]> {
        private static final int SEQUENTIAL_CUTOFF = 500; //TODO: figure out what this should be

        int lo;
        int hi;
        CensusData censusData;

        VTwoDivide(CensusData censusData, int l, int h) {
            this.censusData = censusData;
            this.lo = l;
            this.hi = h;
        }
        protected double[] compute() {
            if((hi - lo) < SEQUENTIAL_CUTOFF) {

                double[] ans = {Double.MAX_VALUE, Double.MIN_VALUE, Double.MAX_VALUE, Double.MIN_VALUE, 0.};

                for(int i=lo; i < hi; i++) {
                    double lat = (double)censusData.data[i].latitude;
                    double lon = (double)censusData.data[i].longitude;
                    double pop = (double)censusData.data[i].population;
                    ans[0] = Math.min(ans[0], lat);
                    ans[1] = Math.max(ans[1], lat);
                    ans[2] = Math.min(ans[2], lon);
                    ans[3] = Math.max(ans[3], lon);
                    ans[4] += pop;
                }
                return ans;
            } else {
                VTwoDivide left = new VTwoDivide(censusData,lo,(hi+lo)/2);
                VTwoDivide right= new VTwoDivide(censusData,(hi+lo)/2,hi);
                left.fork();
                double[] rightAns = right.compute();
                double[] leftAns  = left.join();
                double[] newAns = new double[5];
                newAns[0] = Math.min(rightAns[0], leftAns[0]);
                newAns[1] = Math.max(rightAns[1], leftAns[1]);
                newAns[2] = Math.min(rightAns[2], leftAns[2]);
                newAns[3] = Math.max(rightAns[3], leftAns[3]);
                newAns[4] = rightAns[4] + leftAns[4];
                return newAns;
            }
        }
    }

    static double[] versionTwoDivide(CensusData censusData){
        return fjPool.invoke(new VTwoDivide(censusData,0,censusData.data_size));
    }


    static class VTwoQuery extends RecursiveTask<Integer> {
        private static final int SEQUENTIAL_CUTOFF = 500; //TODO: figure out what this should be

        int lo;
        int hi;
        CensusData censusData;
        double[] sizes;
        int[] borders;

        VTwoQuery(CensusData censusData, double[] sizes, int[] borders, int l, int h) {
            this.censusData = censusData;
            this.sizes = sizes;
            this.borders = borders;
            this.lo = l;
            this.hi = h;

        }
        protected Integer compute() {
            if((hi - lo) < SEQUENTIAL_CUTOFF) {
                int ans = 0;
                for(int i=lo; i < hi; i++) {
                    double curX = (censusData.data[i].longitude - minLong) / sizes[0]; //colSize
                    double curY = (censusData.data[i].latitude - minLat) / sizes[1]; //rowSize
                    int column = (int)curX + 1;
                    int row = (int)curY + 1;

                    if ((column >= borders[0]) && (column <= borders[2]) && (row >= borders[1]) && (row <= borders[3])) {
                        ans += censusData.data[i].population;
                    }
                }
                return ans;
            } else {
                VTwoQuery left = new VTwoQuery(censusData, sizes, borders, lo,(hi+lo)/2);
                VTwoQuery right= new VTwoQuery(censusData, sizes, borders, (hi+lo)/2,hi);
                left.fork();
                int rightAns = right.compute();
                int leftAns  = left.join();
                return leftAns + rightAns;
            }
        }
    }

    static int versionTwoQuery(CensusData censusData, double[] sizes, int[] borders){
        return fjPool.invoke(new VTwoQuery(censusData, sizes, borders, 0,censusData.data_size));
    }



    public static int[][] versionThreeDivide(CensusData censusData, int x, int y, double[] sizes) {

        int[][] grid = new int[x][y];
        for (int i = 0; i < censusData.data_size; i++) {
            Double curX = (censusData.data[i].longitude - minLong) / sizes[0];
            Double curY = (censusData.data[i].latitude - minLat) / sizes[1];
            int column = curX.intValue();
            int row = curY.intValue();
            if (column == x) {
                column--;
            }
            if (row == y) {
                row--;
            }
            grid[column][row] += censusData.data[i].population;
        }
        for (int i = 0; i < x; i++) {
            for(int j = y - 1; j >= 0; j--) {
                if (i != 0) {
                    grid[i][j] += grid[i - 1][j];
                }
                if (j != y - 1) {
                    grid[i][j] += grid[i][j + 1];
                }
                if (i != 0 && j != y - 1) {
                    grid[i][j] -= grid[i - 1][j + 1];
                }
            }
        }
        return grid;
    }

    public static int versionThreeQuery(int[][] grid, int west, int south, int east, int north) {
        int population = grid[east - 1][south - 1];
        if (west != 1) {
            population -= grid[west - 2][south - 1];
        }
        if (north != grid[0].length) {
            population -= grid[east - 1][north];
        }
        if (north != grid[0].length && west != 1) {
            population += grid[west - 2][north];
        }
        return population;
    }

}
