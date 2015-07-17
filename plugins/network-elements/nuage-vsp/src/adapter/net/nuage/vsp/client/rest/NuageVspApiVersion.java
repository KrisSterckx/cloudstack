package net.nuage.vsp.client.rest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

/**
 * Created by maximusf on 6/18/15.
 */
public class NuageVspApiVersion implements Comparable<NuageVspApiVersion> {
    private static final Pattern versionRegEx = Pattern.compile("v(\\d+)_(\\d+)");
    public static final NuageVspApiVersion V3_2 = new NuageVspApiVersion(3, 2);

    private Integer major;
    private Integer minor;

    public NuageVspApiVersion(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    public NuageVspApiVersion(String version) {
        final Matcher matcher = versionRegEx.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Bad version");
        }

        major = Integer.valueOf(matcher.group(1));
        minor = Integer.valueOf(matcher.group(2));
    }

    @Override
    public boolean equals(Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public int compareTo(NuageVspApiVersion other) {
        int compare = major.compareTo(other.major);
        if (compare == 0) {
            compare = minor.compareTo(other.minor);
        }

        return compare;
    }

    @Override
    public String toString() {
        return String.format("v%d_%d", major, minor);
    }
}
