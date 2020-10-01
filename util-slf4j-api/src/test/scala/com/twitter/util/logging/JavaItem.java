package com.twitter.util.logging;

class JavaItem extends SecondItem {
    private static final Logger LOG = Logger.getLogger(JavaItem.class);

    public JavaItem(String name, String description, int size, int count) {
        super(name, description, size, count);
        logger().info("Creating new JavaItem using inherited logger.");
        LOG.info("Creating new JavaItem with class logger.");
    }
}
