/*
 * Copyright 2005,2009 Ivan SZKIBA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ini4j;

import org.ini4j.spi.IniBuilder;
import org.ini4j.spi.IniFormatter;
import org.ini4j.spi.IniHandler;
import org.ini4j.spi.IniParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

import java.net.URL;
import java.nio.charset.Charset;

public class Ini extends BasicProfile implements Persistable, Configurable
{
    // Byte-order mark for UTF8 and UTF16 files
    private static final int FIRST_UTF8_BYTE = 0xEF;
    private static final int SECOND_UTF8_BYTE = 0xBB;
    private static final int THIRD_UTF8_BYTE = 0xBF;
    
    private static final int FIRST_UTF16_BIGENDIAN = 0xFE;
    private static final int SECOND_UTF16_BIGENDIAN = 0xFF;

    private static final int FIRST_UTF16_LITTLEENDIAN = 0xFF;
    private static final int SECOND_UTF16_LITTLEENDIAN = 0xFE;

    private static final long serialVersionUID = -6029486578113700585L;
    
    private Config _config;
    private File _file;

    public Ini()
    {
        _config = Config.getGlobal();
    }

    public Ini(Reader input) throws IOException, InvalidFileFormatException
    {
        this();
        load(input);
    }

    public Ini(InputStream input) throws IOException, InvalidFileFormatException
    {
        this();
        
        boolean resetMustBeDone = false;
        final boolean resetIsSupported = input.markSupported();
        final Reader originalInputStream = new InputStreamReader( input );
        
        if (resetIsSupported)
        {
            input.mark( 1024 );
        }
        final int firstReadByte = input.read();
        if (   (firstReadByte == FIRST_UTF16_BIGENDIAN)
            || (firstReadByte == FIRST_UTF16_LITTLEENDIAN)
            || (firstReadByte == FIRST_UTF8_BYTE) )
        {
            final int secondReadByte = input.read();
            if (   (firstReadByte == FIRST_UTF16_BIGENDIAN)
                && (secondReadByte == SECOND_UTF16_BIGENDIAN) )
            {
                //we have detected UTF16 BE
                _config.setFileEncoding( Charset.forName( "UTF-16BE" ) );
            }
            else if (  (firstReadByte == FIRST_UTF16_LITTLEENDIAN)
                     && (secondReadByte == SECOND_UTF16_LITTLEENDIAN) )
            {
                //we have detected UTF16 LE
                _config.setFileEncoding( Charset.forName( "UTF-16LE" ) );
            }
            else if (   (firstReadByte == FIRST_UTF8_BYTE)
                     && (secondReadByte == SECOND_UTF8_BYTE) )
            {
                final int thirdReadByte = input.read();
                if (   (firstReadByte == FIRST_UTF8_BYTE)
                    && (secondReadByte == SECOND_UTF8_BYTE)
                    && (thirdReadByte == THIRD_UTF8_BYTE) )
                {
                    //we have detected UTF8
                    _config.setFileEncoding( Charset.forName( "UTF-8" ) );
                }
                else
                {
                    resetMustBeDone = true;
                }   
            }
            else
            {
                resetMustBeDone = true;    
            }
        }
        else
        {
            resetMustBeDone = true;
        }
        
        if (resetMustBeDone)
        {
            if (resetIsSupported)
            {
                input.reset();
                load(input);
            }
            else
            {
                load(originalInputStream);
            }   
        }
        else
        {
            load(input);
        }
    }

    public Ini(URL input) throws IOException, InvalidFileFormatException
    {
        this();
        load(input);
    }

    public Ini(File input) throws IOException, InvalidFileFormatException
    {
        this();
        _file = input;
        load();
    }

    @Override public Config getConfig()
    {
        return _config;
    }

    @Override public void setConfig(Config value)
    {
        _config = value;
    }

    @Override public File getFile()
    {
        return _file;
    }

    @Override public void setFile(File value)
    {
        _file = value;
    }

    @Override public void load() throws IOException, InvalidFileFormatException
    {
        if (_file == null)
        {
            throw new FileNotFoundException();
        }

        load(_file);
    }

    @Override public void load(InputStream input) throws IOException, InvalidFileFormatException
    {
        load(new InputStreamReader(input, getConfig().getFileEncoding()));
    }

    @Override public void load(Reader input) throws IOException, InvalidFileFormatException
    {
        IniParser.newInstance(getConfig()).parse(input, newBuilder());
    }

    @Override public void load(File input) throws IOException, InvalidFileFormatException
    {
        load(input.toURI().toURL());
    }

    @Override public void load(URL input) throws IOException, InvalidFileFormatException
    {
        IniParser.newInstance(getConfig()).parse(input, newBuilder());
    }

    @Override public void store() throws IOException
    {
        if (_file == null)
        {
            throw new FileNotFoundException();
        }

        store(_file);
    }

    @Override public void store(OutputStream output) throws IOException
    {
        store(new OutputStreamWriter(output, getConfig().getFileEncoding()));
    }

    @Override public void store(Writer output) throws IOException
    {
        store(IniFormatter.newInstance(output, getConfig()));
    }

    @Override public void store(File output) throws IOException
    {
        OutputStream stream = new FileOutputStream(output);

        store(stream);
        stream.close();
    }

    protected IniHandler newBuilder()
    {
        return IniBuilder.newInstance(this);
    }

    @Override protected void store(IniHandler formatter, Profile.Section section)
    {
        if (getConfig().isEmptySection() || (section.size() != 0))
        {
            super.store(formatter, section);
        }
    }

    @Override protected void store(IniHandler formatter, Profile.Section section, String option, int index)
    {
        if (getConfig().isMultiOption() || (index == (section.length(option) - 1)))
        {
            super.store(formatter, section, option, index);
        }
    }

    @Override boolean isTreeMode()
    {
        return getConfig().isTree();
    }

    @Override char getPathSeparator()
    {
        return getConfig().getPathSeparator();
    }

    @Override boolean isPropertyFirstUpper()
    {
        return getConfig().isPropertyFirstUpper();
    }
}
