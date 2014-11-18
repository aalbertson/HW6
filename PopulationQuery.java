
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
    public static final int TOKENS_PER_LINE = 7;
    public static final int POPULATION_INDEX = 4; // zero-based indices
    public static final int LATITUDE_INDEX = 5;
    public static final int LONGITUDE_INDEX = 6;

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
                if (tokens.length != TOKENS_PER_LINE)
                    throw new NumberFormatException();
                int population = Integer.parseInt(tokens[POPULATION_INDEX]);
                if (population != 0)
                    result.add(population,
                            Float.parseFloat(tokens[LATITUDE_INDEX]),
                            Float.parseFloat(tokens[LONGITUDE_INDEX]));
            }

            fileIn.close();
        } catch (IOException ioe) {
            System.err.println("Error opening/reading/writing input or output file.");
            System.exit(1);
        } catch (NumberFormatException nfe) {
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

        int[][] grid = new int[x][y];
        Data data = new Data();
        if (version.equals("-v1") || version.equals("-v3")) {
            double[] corners = versionOneDivide(censusData);
            data = new Data(censusData, corners, x, y);
            if (version.equals("-v3")) {
                grid = versionThreeDivide(data);
                grid = versionThreeAlter(grid);
            }

        } else if (version.equals("-v2") || version.equals("-v4")) {
            double[] corners = versionTwoDivide(censusData);
            data = new Data(censusData, corners, x, y);
            if (version.equals("-v4")) {
                grid = versionFourDivide(data);
            }
        }

        boolean cont = true;
        while (cont) {
            System.out.println("Please give west, south, east, north coordinates of your query");
            System.out.println("  rectangle:");
            String rect = console.nextLine();

            String[] borders = rect.split("[ \t]+");

            if (borders.length == 4) {
                int west = Integer.parseInt(borders[0]);
                int south = Integer.parseInt(borders[1]);
                int east = Integer.parseInt(borders[2]);
                int north = Integer.parseInt(borders[3]);

                if ((west < 1) || (west > x) || (south < 1) || (south > y)
                        || (east < west) || (east > x) || (north < south) || (north > y)) {
                    throw new IllegalArgumentException();
                }
                int[] border = {west, south, east, north};
                int queryPop = 0;
                if (version.equals("-v1")) {
                    queryPop = versionOneQuery(data, border);
                } else if (version.equals("-v2")) {
                    queryPop = versionTwoQuery(data, border);
                } else if (version.equals("-v3") || version.equals("-v4")) {
                    int totalPopulation = 0;
                    queryPop = versionThreeQuery(grid, border);
                }
                double percent = (double) queryPop / data.totalPop();
                percent = Math.round(percent * 100 * 100);
                percent = percent / 100;
                String perc = String.format("%.2f", percent);

                System.out.println("population of rectangle: " + queryPop);
                System.out.println("percent of total population: " + perc);
            } else {
                cont = false;
            }
        }
    }

    public static double[] versionOneDivide(CensusData censusData) {
        double[] borders = new double[5];
        borders[0] = (double)censusData.data[0].longitude;
        borders[1] = (double)censusData.data[0].longitude;
        borders[2] = (double)censusData.data[0].latitude;
        borders[3] = (double)censusData.data[0].latitude;
        borders[4] = (double)censusData.data[0].population;

        // compute US rectangle size and total population
        for (int i = 1; i < censusData.data_size; i++) {
            float lat = censusData.data[i].latitude;
            float lon = censusData.data[i].longitude;
            borders[0] = Math.min(borders[0], (double)lon);
            borders[1] = Math.max(borders[1], (double)lon);
            borders[2] = Math.min(borders[2], (double)lat);
            borders[3] = Math.max(borders[3], (double)lat);

            borders[4] += (double)censusData.data[i].population;
        }
        return borders;
    }

    public static int versionOneQuery(Data data, int[] border) {
        int queryPop = 0;
        for (int i = 0; i < data.censusData.data_size; i++) {
            double curX = (data.censusData.data[i].longitude - data.minLong()) / data.colSize();
            double curY = (data.censusData.data[i].latitude - data.minLat()) / data.rowSize();
            int column = (int) curX + 1;
            int row = (int) curY + 1;
            if (column == data.x + 1) {
                column--;
            }
            if (row == data.y + 1) {
                row--;
            }
            if ((column >= border[0]) && (row >= border[1]) && (column <= border[2]) && (row <= border[3])) {
                queryPop += data.censusData.data[i].population;
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
            if ((hi - lo) < SEQUENTIAL_CUTOFF) {

                double[] ans = {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0};

                for (int i = lo; i < hi; i++) {
                    double lat = censusData.data[i].latitude;
                    double lon = censusData.data[i].longitude;
                    double pop = censusData.data[i].population;
                    ans[0] = Math.min(ans[0], lon);
                    ans[1] = Math.max(ans[1], lon);
                    ans[2] = Math.min(ans[2], lat);
                    ans[3] = Math.max(ans[3], lat);
                    ans[4] += pop;
                }
                return ans;
            } else {
                VTwoDivide left = new VTwoDivide(censusData, lo, (hi + lo) / 2);
                VTwoDivide right = new VTwoDivide(censusData, (hi + lo) / 2, hi);
                left.fork();
                double[] rightAns = right.compute();
                double[] leftAns = left.join();
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

    static double[] versionTwoDivide(CensusData censusData) {
        return fjPool.invoke(new VTwoDivide(censusData, 0, censusData.data_size));
    }


    static class VTwoQuery extends RecursiveTask<Integer> {
        private static final int SEQUENTIAL_CUTOFF = 500; //TODO: figure out what this should be
        int[] border;
        Data data;
        int lo;
        int hi;

        VTwoQuery(Data data, int[] border, int l, int h) {
            this.border = border;
            this.data = data;
            this.lo = l;
            this.hi = h;
        }

        protected Integer compute() {
            if ((hi - lo) < SEQUENTIAL_CUTOFF) {

                int ans = 0;
                for (int i = lo; i < hi; i++) {
                    double curX = (data.censusData.data[i].longitude - data.minLong()) / data.colSize();
                    double curY = (data.censusData.data[i].latitude - data.minLat()) / data.rowSize();
                    int column = (int) curX + 1;
                    int row = (int) curY + 1;
                    if (column == data.x + 1) {
                        column--;
                    }
                    if (row == data.y + 1) {
                        row--;
                    }

                    if ((column >= border[0]) && (column <= border[2]) && (row >= border[1]) && (row <= border[3])) {
                        ans += data.censusData.data[i].population;
                    }
                }
                return ans;
            } else {
                VTwoQuery left = new VTwoQuery(data, border, lo, (hi + lo) / 2);
                VTwoQuery right = new VTwoQuery(data, border, (hi + lo) / 2, hi);
                left.fork();
                int rightAns = right.compute();
                int leftAns = left.join();
                return leftAns + rightAns;
            }
        }
    }

    static int versionTwoQuery(Data data, int[] borders) {
        return fjPool.invoke(new VTwoQuery(data, borders, 0, data.censusData.data_size));
    }


    public static int[][] versionThreeDivide(Data data) {

        int[][] grid = new int[data.x][data.y];
        for (int i = 0; i < data.censusData.data_size; i++) {
            double curX = (data.censusData.data[i].longitude - data.minLong()) / data.colSize();
            double curY = (data.censusData.data[i].latitude - data.minLat()) / data.rowSize();
            int column = (int) curX;
            int row = (int) curY;
            if (column == data.x) {
                column--;
            }
            if (row == data.y) {
                row--;
            }
            grid[column][row] += data.censusData.data[i].population;
        }
        return grid;
    }

    public static int[][] versionThreeAlter(int[][] grid) {
        for (int i = 0; i < grid.length; i++) {
            for (int j = grid[i].length - 1; j >= 0; j--) {
                if (i != 0) {
                    grid[i][j] += grid[i - 1][j];
                }
                if (j != grid[i].length - 1) {
                    grid[i][j] += grid[i][j + 1];
                }
                if (i != 0 && j != grid[i].length - 1) {
                    grid[i][j] -= grid[i - 1][j + 1];
                }
            }
        }
        return grid;
    }

    public static int versionThreeQuery(int[][] grid, int[] border) {
        int population = grid[border[2] - 1][border[1] - 1];
        if (border[0] != 1) {
            population -= grid[border[0] - 2][border[1] - 1];
        }
        if (border[3] != grid[0].length) {
            population -= grid[border[2] - 1][border[3]];
        }
        if (border[3] != grid[0].length && border[0] != 1) {
            population += grid[border[0] - 2][border[3]];
        }
        return population;
    }

    static class VFourMakeGrid extends RecursiveTask<int[][]> {
        private static final int SEQUENTIAL_CUTOFF = 500; //TODO: figure out what this should be
        Data data;
        int low;
        int high;

        VFourMakeGrid(Data data, int l, int h) {
            this.data = data;
            this.low = l;
            this.high = h;
        }

        protected int[][] compute() {
            if ((high - low) < SEQUENTIAL_CUTOFF) {
                int[][] grid = new int[data.x][data.y];
                for (int i = low; i < high; i++) {
                    double curX = (data.censusData.data[i].longitude - data.minLong()) / data.colSize();
                    double curY = (data.censusData.data[i].latitude - data.minLat()) / data.rowSize();
                    int column = (int) curX;
                    int row = (int) curY;
                    if (column == data.x) {
                        column--;
                    }
                    if (row == data.y) {
                        row--;
                    }
                    grid[column][row] += data.censusData.data[i].population;
                }
                return grid;
            } else {
                VFourMakeGrid left = new VFourMakeGrid(data, low, (high + low) / 2);
                VFourMakeGrid right = new VFourMakeGrid(data, (high + low) / 2, high);
                left.fork();
                int[][] gridRight = right.compute();
                int[][] gridLeft = left.join();
                return fjPool.invoke(new VFourAddGrids(gridLeft, gridRight, 0, 0, data.x, data.y));

            }
        }
    }

    static class VFourAddGrids extends RecursiveTask<int[][]> {
        private static final int SEQUENTIAL_CUTOFF = 100;

        int[][] gridLeft;
        int[][] gridRight;
        int lowX;
        int lowY;
        int highX;
        int highY;

        public VFourAddGrids (int[][] gridLeft, int[][] gridRight, int lowX, int lowY, int highX, int highY) {
            this.gridLeft = gridLeft;
            this.gridRight = gridRight;
            this.lowX = lowX;
            this.lowY = lowY;
            this.highX = highX;
            this.highY = highY;
        }

        protected int[][] compute() {
            int size = (highX - lowX) * (highY - lowY);
            if(size <= SEQUENTIAL_CUTOFF) {
                for(int x = lowX; x < highX; x++) {
                    for (int y = lowY; y < highY; y++) {
                        gridLeft[x][y] += gridRight[x][y];
                    }
                }
                return gridLeft;
            } else {
                if (highY - lowY > highX - lowX) {
                    VFourAddGrids bottom = new VFourAddGrids(gridLeft, gridRight, lowX, lowY, highX, (lowY + highY) / 2);
                    VFourAddGrids top = new VFourAddGrids(gridLeft, gridRight, lowX, (lowY + highY) / 2, highX, highY);
                    bottom.fork();
                    top.compute();
                    bottom.join();
                    return gridLeft;
                } else {
                    VFourAddGrids left = new VFourAddGrids(gridLeft, gridRight, lowX, lowY, (highX + lowX) / 2, highY);
                    VFourAddGrids right = new VFourAddGrids(gridLeft, gridRight, (lowX + highX) / 2, lowY, highX, highY);
                    left.fork();
                    right.compute();
                    left.join();
                    return gridLeft;
                }
            }
        }
    }

    static int[][] versionFourDivide(Data data) {
        int[][] grid = fjPool.invoke(new VFourMakeGrid(data, 0, data.censusData.data_size));
        grid = versionThreeAlter(grid);
        return grid;
    }

    static class Data {
        CensusData censusData;
        double[] borders;
        int x;
        int y;

        public Data (CensusData censusData, double[] borders, int x, int y) {
            this.censusData = censusData;
            this.borders = borders;
            this.x = x;
            this.y = y;
        }

        public Data () {
            this.censusData = null;
            this.borders = null;
            this.x = 0;
            this.y = 0;
        }

        public double colSize () {
            return (double) (borders[1] - borders[0]) / x;
        }

        public double rowSize () {
            return (double) (borders[3] - borders[2]) / y;
        }

        public double minLong () {
            return borders[0];
        }

        public double minLat () {
            return borders[2];
        }

        public int totalPop () {
            return (int) borders[4];
        }
    }
}
