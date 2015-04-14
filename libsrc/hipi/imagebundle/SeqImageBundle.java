package hipi.imagebundle;

import hipi.image.FloatImage;
import hipi.image.ImageHeader;
import hipi.image.ImageHeader.ImageType;
import hipi.image.io.CodecManager;
import hipi.image.io.ImageDecoder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.Writer;
import org.apache.hadoop.io.SequenceFile.Reader;

public class SeqImageBundle extends AbstractImageBundle {

  public SeqImageBundle(Path path, Configuration conf) {
    super(path, conf);
  }

  private SequenceFile.Writer writer;
  private SequenceFile.Reader reader;
  private long total;
  private byte cacheData[];
  private int cacheType;

  @Override
  protected void openForWrite() throws IOException {
    // _writer = SequenceFile.createWriter(FileSystem.get(_conf), _conf, _file_path,
    // LongWritable.class, BytesWritable.class);
    _writer =
        SequenceFile.createWriter(_conf, Writer.file(_file_path),
            Writer.keyClass(LongWritable.class), Writer.valueClass(BytesWritable.class));
    _total = 1;
  }

  @Override
  protected void openForRead() throws IOException {
    // _reader = new SequenceFile.Reader(FileSystem.get(_conf), _file_path, _conf);
    _reader = new Reader(_conf, Reader.file(_file_path));
    _cacheData = null;
    _cacheType = 0;
  }

  @Override
  public void addImage(InputStream image_stream, ImageType type) throws IOException {
    byte data[] = new byte[image_stream.available()];
    image_stream.read(data);
    long sig = (_total << 2) | type.toValue();
    _writer.append(new LongWritable(sig), new BytesWritable(data));
    _total++;
  }

  /* SeqImageBundle is unable to count number of images */
  @Override
  public long getImageCount() {
    return -1;
  }

  @Override
  protected ImageHeader readHeader() throws IOException {
    if (_cacheData != null) {
      ImageDecoder decoder = CodecManager.getDecoder(ImageType.fromValue(_cacheType));
      if (decoder == null)
        return null;
      ByteArrayInputStream bis = new ByteArrayInputStream(_cacheData);
      ImageHeader header = decoder.decodeImageHeader(bis);
      bis.close();
      return header;
    }
    return null;
  }

  @Override
  protected FloatImage readImage() throws IOException {
    if (_cacheData != null) {
      ImageDecoder decoder = CodecManager.getDecoder(ImageType.fromValue(_cacheType));
      if (decoder == null)
        return null;
      ByteArrayInputStream bis = new ByteArrayInputStream(_cacheData);
      FloatImage image = decoder.decodeImage(bis);
      bis.close();
      return image;
    }
    return null;
  }

  @Override
  protected boolean prepareNext() {
    try {
      LongWritable key = new LongWritable();
      BytesWritable data = new BytesWritable();
      if (_reader.next(key, data)) {
        _cacheType = (int) (key.get() & 0x3);
        _cacheData = data.getBytes();
        return true;
      } else {
        return false;
      }
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public void close() throws IOException {
    if (_reader != null) {
      _reader.close();
    }
    if (_writer != null) {
      _writer.close();
    }
  }

}
