package com.tarterware.roadrunner.experiments;

import java.util.ArrayList;

import weka.clusterers.SimpleKMeans;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

/**
 * This class demonstrates the use of the k-means clustering algorithm using
 * Weka to group geographic points into clusters.
 * 
 * <p>
 * The purpose of this demonstration is to show how k-means can be used to pool
 * geographically nearby vehicles together. This approach could be utilized in a
 * production environment for optimizing load balancing, resource allocation, or
 * vehicle assignment on various application instances.
 * </p>
 * 
 * <p>
 * Note: This class is for experimental purposes only and is not intended for
 * direct use in production systems.
 * </p>
 * 
 * <p>
 * Steps: 1. Create a dataset of geographic points (longitude, latitude). 2.
 * Apply k-means clustering to group the points into clusters. 3. Display the
 * cluster centroids and the assignments of points to clusters.
 */
public class WekaKMeansExample
{
    /**
     * Main method to demonstrate k-means clustering on a set of geographic points.
     * 
     * @param args Command-line arguments (not used)
     * @throws Exception if an error occurs during clustering
     */
    public static void main(String[] args) throws Exception
    {
        // Step 1: Create dataset with geographic points (don't include in the execution
        // time to calculate k-means)
        Instances dataset = createDataset();

        // Record the start time for measuring execution time
        long nsStartTime = System.nanoTime();

        // Step 2: Configure and run k-means clustering
        int numberOfClusters = 3; // Number of clusters
        SimpleKMeans kMeans = new SimpleKMeans();
        kMeans.setNumClusters(numberOfClusters);
        kMeans.setSeed(10); // For reproducibility

        // Build the clustering model
        kMeans.buildClusterer(dataset);

        // Step 3: Output the results
        System.out.println("Cluster Centroids:");
        Instances centroids = kMeans.getClusterCentroids();
        for (int i = 0; i < centroids.numInstances(); i++)
        {
            System.out.printf("Centroid %d: [%f, %f]%n", i + 1, centroids.instance(i).value(0),
                    centroids.instance(i).value(1));
        }

        // Assign each point to a cluster and print the assignments
        System.out.println("\nCluster Assignments:");
        for (int i = 0; i < dataset.numInstances(); i++)
        {
            int cluster = kMeans.clusterInstance(dataset.instance(i));
            System.out.printf("Point %d -> Cluster %d%n", i + 1, cluster + 1);
        }

        // Record the end time and calculate execution duration
        long nsEndTime = System.nanoTime();
        double msExecutionTime = (nsEndTime - nsStartTime) / 1_000_000.0;
        System.out.println("Total execution time: " + msExecutionTime);
    }

    /**
     * Creates a dataset of geographic points with longitude and latitude
     * attributes.
     * 
     * @return An Instances object containing the geographic points dataset
     */
    private static Instances createDataset()
    {
        // Define the attributes (longitude, latitude)
        Attribute longitude = new Attribute("longitude");
        Attribute latitude = new Attribute("latitude");

        // Add attributes to the dataset
        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(longitude);
        attributes.add(latitude);

        // Create the dataset
        Instances dataset = new Instances("GeographicPoints", attributes, 0);

        // Example points (longitude, latitude)
        double[][] points =
            {
                    { -73.935242, 40.730610 }, // New York
                    { -118.243683, 34.052235 }, // Los Angeles
                    { -87.623177, 41.881832 }, // Chicago
                    { -122.419418, 37.774929 }, // San Francisco
                    { -95.369804, 29.760427 }, // Houston
                    { -80.191788, 25.761681 }, // Miami
                    { -104.990250, 39.739235 }, // Denver
                    { -90.048980, 35.149532 }, // Memphis
                    { -84.388229, 33.749001 }, // Atlanta
                    { -77.036873, 38.907192 } // Washington DC
            };

        // Add the points to the dataset
        for (double[] point : points)
        {
            double[] values = new double[]
                { point[0], point[1] };
            dataset.add(new DenseInstance(1.0, values));
        }

        return dataset;
    }
}
