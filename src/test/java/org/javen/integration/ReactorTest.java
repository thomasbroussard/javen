package org.javen.integration;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.javen.integration.Reactor.*;

import java.io.File;

public class ReactorTest {


    public static final String TARGET_DELIVERY = "target/delivery";

    @Before
    public void init(){
        createDirs(TARGET_DELIVERY);
    }

    @Test
    public void testCopyDir(){
        Reactor.copy("src/test/resources/copy/copyDir", TARGET_DELIVERY);
        Assert.assertTrue(new File(TARGET_DELIVERY + "/testDir/subTest.json").exists());
    }


    @Test
    public void testCopyFile(){
        Reactor.copy("src/test/resources/copy/copyDir/test.json", TARGET_DELIVERY);
        Assert.assertTrue(new File(TARGET_DELIVERY + "/test.json").exists());
    }


    @After
    public void clean(){
        delete(TARGET_DELIVERY);
    }

}
