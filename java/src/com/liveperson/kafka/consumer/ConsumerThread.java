package com.liveperson.kafka.consumer;

import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.message.MessageAndMetadata;
import java.io.BufferedOutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by elio on 4/17/2016.
 */
public class ConsumerThread implements Runnable {

    private boolean paused;
    private KafkaStream stream;
    private int threadNumber;
    private ThreadExceptionListener exceptionListener;
    private int consumerServerPort;
    private AtomicBoolean isPaused;

    public ConsumerThread(KafkaStream stream, int threadNumber, int consumerServerPort, ThreadExceptionListener exceptionListener, AtomicBoolean isPaused) {
        this.threadNumber = threadNumber;
        this.stream = stream;
        this.exceptionListener = exceptionListener;
        this.consumerServerPort = consumerServerPort;
        this.isPaused = isPaused;
    }

    @Override
    public void run() {

        Socket clientSocket = null;
        try{
            ConsumerIterator<byte[], byte[]> it = stream.iterator();

            clientSocket = new Socket("localhost", consumerServerPort);
            BufferedOutputStream outToServer = new BufferedOutputStream(clientSocket.getOutputStream(), 8192);
            while (it.hasNext()) {
                synchronized (isPaused){
                  while (isPaused.get()){
                    isPaused.wait();
                  }
                }
                MessageAndMetadata<byte[], byte[]> messageAndMetadata = it.next();
                byte[] msg = messageAndMetadata.message();
                byte[] topic = messageAndMetadata.topic().getBytes();
                long offset = messageAndMetadata.offset();
                int partition = messageAndMetadata.partition();
                outToServer.write(ByteBuffer.allocate(4).putInt(msg.length+4+topic.length+8+4).array());
                outToServer.write(ByteBuffer.allocate(4).putInt(topic.length).array()); //4
                outToServer.write(ByteBuffer.allocate(8).putLong(offset).array()); //8
                outToServer.write(ByteBuffer.allocate(4).putInt(partition).array()); //4
                outToServer.write(topic); //topic.length
                outToServer.write(msg);
                outToServer.flush();
            }

            clientSocket.shutdownOutput();
        }catch(Exception ex){
            exceptionListener.onThreadException(threadNumber, ex);
            if(clientSocket != null){
                try{
                    clientSocket.shutdownOutput();
                }catch(Exception e){

                }
            }
        }
    }
}
