package jdepend.framework;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * The <code>JavaClassBuilder</code> builds <code>JavaClass</code>
 * instances from .class, .jar, .war, or .zip files.
 *
 * @author <b>Mike Clark</b>
 * @author Clarkware Consulting, Inc.
 */

public class JavaClassBuilder {

    private final AbstractParser parser;
    private final FileManager fileManager;


    public JavaClassBuilder() {
        this(new ClassFileParser(), new FileManager());
    }

    public JavaClassBuilder(FileManager fm) {
        this(new ClassFileParser(), fm);
    }

    public JavaClassBuilder(AbstractParser parser, FileManager fm) {
        this.parser = parser;
        this.fileManager = fm;
    }

    public int countClasses() {
        AbstractParser counter = new AbstractParser() {

            @Override
            public JavaClass parse(InputStream is) {
                return new JavaClass("");
            }
        };

        JavaClassBuilder builder = new JavaClassBuilder(counter, fileManager);
        Collection classes = builder.build();
        return classes.size();
    }

    /**
     * Builds the <code>JavaClass</code> instances.
     *
     * @return Collection of <code>JavaClass</code> instances.
     */
    public Collection build() {

        Collection classes = new ArrayList();

        for (Iterator i = fileManager.extractFiles().iterator(); i.hasNext();) {

            File nextFile = (File)i.next();

            try {

                classes.addAll(buildClasses(nextFile));

            } catch (IOException ioe) {
                System.err.println("\n" + ioe.getMessage());
            }
        }

        return classes;
    }

    /**
     * Builds the <code>JavaClass</code> instances from the
     * specified file.
     *
     * @param file Class or Jar file.
     * @return Collection of <code>JavaClass</code> instances.
     */
    public Collection buildClasses(File file) throws IOException {

        if (fileManager.acceptClassFile(file)) {
            InputStream is = null;
            try {
                is = new BufferedInputStream(new FileInputStream(file));
                JavaClass parsedClass = parser.parse(is);
                Collection javaClasses = new ArrayList();
                javaClasses.add(parsedClass);
                return javaClasses;
            } finally {
                if (is != null) {
                    is.close();
                }
            }
        } else if (fileManager.acceptJarFile(file)) {

            JarFile jarFile = new JarFile(file);
            Collection result = buildClasses(jarFile);
            jarFile.close();
            return result;

        } else {
            throw new IOException("File is not a valid " +
                    ".class, .jar, .war, or .zip file: " +
                    file.getPath());
        }
    }

    /**
     * Builds the <code>JavaClass</code> instances from the specified
     * jar, war, or zip file.
     *
     * @param file Jar, war, or zip file.
     * @return Collection of <code>JavaClass</code> instances.
     */
    public Collection buildClasses(JarFile file) throws IOException {

        Collection javaClasses = new ArrayList();

        Enumeration entries = file.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = (ZipEntry) entries.nextElement();
            if (fileManager.acceptClassFileName(e.getName())) {
                InputStream is = null;
                try {
                    is = new BufferedInputStream(file.getInputStream(e));
                    JavaClass jc = parser.parse(is);
                    javaClasses.add(jc);
                } finally {
                    if(is != null) {
                        is.close();
                    }
                }
            }
        }

        return javaClasses;
    }
}
