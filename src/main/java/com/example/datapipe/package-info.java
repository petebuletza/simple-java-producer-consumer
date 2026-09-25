/**
 * A dependency-light producer/consumer pair: the producer streams randomly
 * generated {@link com.example.datapipe.DataItem}s over TCP, and the consumer
 * reads and tallies them. Both roles are launched from
 * {@link com.example.datapipe.Main}, or directly via
 * {@link com.example.datapipe.ProducerService#main(String[])} /
 * {@link com.example.datapipe.ConsumerService#main(String[])}.
 */
package com.example.datapipe;
