/**
 * Copyright (C) 2016 LibRec
 * <p>
 * This file is part of LibRec.
 * LibRec is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * LibRec is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with LibRec. If not, see <http://www.gnu.org/licenses/>.
 */
package net.librec.data.convertor.appender;

import com.google.common.collect.*;
import net.librec.conf.Configuration;
import net.librec.conf.Configured;
import net.librec.data.DataAppender;
import net.librec.math.structure.DenseMatrix;
import net.librec.math.structure.DenseVector;
import net.librec.math.structure.SparseMatrix;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * A <tt>SocialDataAppender</tt> is a class to process and store social appender
 * data.
 *
 * @author SunYatong
 */
public class PhotoDataAppender extends Configured implements DataAppender {

    /** The size of the buffer */
    private static final int BSIZE = 1024 * 1024;

    /** a {@code SparseMatrix} object build by the photo data */
    private DenseMatrix itemPhotoMatrix;

    /** The path of the appender data file */
    private String inputDataPath;

    /** User {raw id, inner id} map from rating data */
    private BiMap<String, Integer> userIds;

    /** Item {raw id, inner id} map from rating data */
    private BiMap<String, Integer> itemIds;

    /**
     * Initializes a newly created {@code PhotoDataAppender} object with null.
     */
    public PhotoDataAppender() {
        this(null);
    }

    /**
     * Initializes a newly created {@code PhotoDataAppender} object with a
     * {@code Configuration} object
     *
     * @param conf  {@code Configuration} object for construction
     */
    public PhotoDataAppender(Configuration conf) {
        this.conf = conf;
    }

    /**
     * Process appender data.
     *
     * @throws IOException if I/O error occurs during processing
     */
    @Override
    public void processData() throws IOException {
        if (conf != null && StringUtils.isNotBlank(conf.get("data.appender.photo.path"))) {
            inputDataPath = conf.get("dfs.data.dir") + "/" + conf.get("data.appender.photo.path");
            readData(inputDataPath);
        }
    }

    /**
     * Read data from the data file. Note that we didn't take care of the
     * duplicated lines.
     *
     * @param inputDataPath
     *            the path of the data file
     * @throws IOException if I/O error occurs during reading
     */
    private void readData(String inputDataPath) throws IOException {

        //构造一个DenseMatrix 存储photoMatrix
        itemPhotoMatrix = new DenseMatrix(itemIds.size(), 1000);

        // Table {row-id, col-id, rate}
        Table<Integer, Integer, Double> dataTable = HashBasedTable.create();
        // Map {col-id, multiple row-id}: used to fast build a rating matrix
        Multimap<Integer, Integer> colMap = HashMultimap.create();
        // BiMap {raw id, inner id} userIds, itemIds
        final List<File> files = new ArrayList<File>();
        final ArrayList<Long> fileSizeList = new ArrayList<Long>();
        SimpleFileVisitor<Path> finder = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                fileSizeList.add(file.toFile().length());
                files.add(file.toFile());
                return super.visitFile(file, attrs);
            }
        };
        Files.walkFileTree(Paths.get(inputDataPath), finder);
        long allFileSize = 0;
        for (Long everyFileSize : fileSizeList) {
            allFileSize = allFileSize + everyFileSize.longValue();
        }
        // loop every dataFile collecting from walkFileTree
        for (File dataFile : files) {
            FileInputStream fis = new FileInputStream(dataFile);
            FileChannel fileRead = fis.getChannel();
            ByteBuffer buffer = ByteBuffer.allocate(BSIZE);
            int len;
            String bufferLine = new String();
            byte[] bytes = new byte[BSIZE];
            while ((len = fileRead.read(buffer)) != -1) {
                buffer.flip();
                buffer.get(bytes, 0, len);
                bufferLine = bufferLine.concat(new String(bytes, 0, len)).replaceAll("\r", "\n");
                String[] bufferData = bufferLine.split("(\n)+");
                boolean isComplete = bufferLine.endsWith("\n");
                int loopLength = isComplete ? bufferData.length : bufferData.length - 1;
                for (int i = 0; i < loopLength; i++) {
                    String line = new String(bufferData[i]);
                    String[] data = line.trim().split("[ \t]+");
                    String item = data[0];
                    String vectors = data[1];
                    if (itemIds.containsKey(item) ) {
                        int row = itemIds.get(item);
                        String[] tmp = vectors.split(",");

                        //字符串数组转换成double数组
                        double[] array = new double[tmp.length];
                        for(int j=0; j<tmp.length; j++){
                            array[j] = Double.parseDouble(tmp[j]);
                        }

                        //构造 DenseVector
                        DenseVector dv = new DenseVector(array, true);
                        //构造稠密矩阵
                        itemPhotoMatrix.setRow(row, dv);
                        tmp = null;
                        array = null;
                        dv = null;
                    }
                    data = null;
                    vectors = null;

                }
                if (!isComplete) {
                    bufferLine = bufferData[bufferData.length - 1];
                }
                buffer.clear();
            }
            fileRead.close();
            fis.close();
        }
        dataTable = null;
    }

    /**
     * Get item appender.
     *
     * @return the {@code SparseMatrix} object built by the photo data.
     */
    public DenseMatrix getItemAppender() {
        return itemPhotoMatrix;
    }

    /**
     * Get user appender.
     *
     * @return null
     */
    public DenseMatrix getUserAppender() {
        return null;
    }

    /**
     * Set user mapping data.
     *
     * @param userMappingData
     *            user {raw id, inner id} map
     */
    @Override
    public void setUserMappingData(BiMap<String, Integer> userMappingData) {
        this.userIds = userMappingData;
    }

    /**
     * Set item mapping data.
     *
     * @param itemMappingData
     *            item {raw id, inner id} map
     */
    @Override
    public void setItemMappingData(BiMap<String, Integer> itemMappingData) {
        this.itemIds = itemMappingData;
    }
}
