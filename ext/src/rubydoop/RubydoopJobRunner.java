package rubydoop;


import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.mapreduce.Job;

import org.jruby.Ruby;
import org.jruby.RubySymbol;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.javasupport.JavaUtil;


public class RubydoopJobRunner extends Configured implements Tool {
    public int run(String[] args) throws Exception {
        String jobSetupScript = args[0];
        String[] arguments = Arrays.copyOfRange(args, 1, args.length);

        for (Job job : configureJobs(jobSetupScript, arguments)) {
            if (!job.waitForCompletion(true)) {
                return 1;
            }
        }

        return 0;
    }

    private Map<RubySymbol, Class<?>> proxyClasses(final RubySymbol.SymbolTable symbols) {
        return new HashMap<RubySymbol, Class<?>>() {{
            put(symbols.getSymbol("mapper"), MapperProxy.class);
            put(symbols.getSymbol("reducer"), ReducerProxy.class);
            put(symbols.getSymbol("combiner"), CombinerProxy.class);
        }};
    }

    private List<Job> configureJobs(String jobSetupScript, String[] arguments) throws Exception {
        Ruby runtime = InstanceContainer.createRuntime();
        IRubyObject runnerClass = runtime.evalScriptlet("Rubydoop::Configurator");
        IRubyObject[] args = JavaUtil.convertJavaArrayToRuby(runtime, new Object[] {getConf(), proxyClasses(runtime.getSymbolTable())});
        IRubyObject configuratorInstance = runnerClass.callMethod(runtime.getCurrentContext(), "new", args);
        runtime.defineReadonlyVariable("$rubydoop_configurator", configuratorInstance);
        runtime.defineReadonlyVariable("$rubydoop_arguments", JavaUtil.convertJavaArrayToRubyWithNesting(runtime.getCurrentContext(), arguments));
        runtime.evalScriptlet(String.format("require '%s'", jobSetupScript));
        
        List<Job> jobs = (List<Job>) JavaUtil.unwrapJavaObject(configuratorInstance.callMethod(runtime.getCurrentContext(), "jobs"));

        for (Job job : jobs) {
            job.getConfiguration().set("rubydoop.job_config_script", jobSetupScript);
            job.setJarByClass(getClass());
        }

        runtime.tearDown();

        return jobs;
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new RubydoopJobRunner(), args));
    }
}
