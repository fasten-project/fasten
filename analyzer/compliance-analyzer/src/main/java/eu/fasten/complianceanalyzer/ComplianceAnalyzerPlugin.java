package eu.fasten.analyzer.complianceanalyzer;

import org.pf4j.PluginWrapper;

import org.pf4j.Plugin;

public class ComplianceAnalyzerPlugin extends Plugin {

    public ComplianceAnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    // Connect to Kubernetes
    // Start qmstr master
    // Spin build/analysis/reporting phase


    @Override
    public void start() {

    }

    @Override
    public void stop() {
    }
}


