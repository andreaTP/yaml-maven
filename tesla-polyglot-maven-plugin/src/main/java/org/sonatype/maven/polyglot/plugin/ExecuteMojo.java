/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.maven.polyglot.plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.sonatype.maven.polyglot.PolyglotModelManager;
import org.sonatype.maven.polyglot.execute.ExecuteContext;
import org.sonatype.maven.polyglot.execute.ExecuteManager;
import org.sonatype.maven.polyglot.execute.ExecuteTask;

/**
 * Executes registered {@link org.sonatype.maven.polyglot.execute.ExecuteTask}s.
 *
 * @goal execute
 * @requiresDependencyResolution compile
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 */
public class ExecuteMojo
    extends AbstractMojo
{
    /**
     * @component role="org.sonatype.maven.polyglot.execute.ExecuteManager"
     */
    private ExecuteManager manager;

    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @parameter expression="${taskId}"
     * @required
     */
    private String taskId;

    /**
     * @parameter
     */
    private File nativePom;

    /**
     * @component role="org.sonatype.maven.polyglot.PolyglotModelManager"
     */
    private PolyglotModelManager modelManager;

    
    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        Model model = project.getModel();

        if (log.isDebugEnabled()) {
            log.debug( "Executing task '" + taskId + "' for model: " + model.getId() );
        }
        
        assert manager != null;
        List<ExecuteTask> tasks = manager.getTasks(model);
        // if there are no tasks that means we run in proper maven and 
        // have to load the nativePom to setup the ExecuteManager
        if ( tasks.size() == 0 && nativePom != null ){
            // TODO avoid parsing the nativePom for each task
            tasks = manager.getTasks( modelFromNativePom( log ) );
        }
        
        ExecuteContext ctx = new ExecuteContext()
        {
            public MavenProject getProject() {
                return project;
            }
            
            public File basedir() {
                return project.getBasedir();
            }
            
            public Log log() {
                return getLog();
            }
        };

        for (ExecuteTask task : tasks) {
            if (taskId.equals(task.getId())) {
                log.debug("Executing task: " + task.getId());

                try {
                    task.execute(ctx);
                    return;
                }
                catch (Exception e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        }

        throw new MojoFailureException("Unable to find task for id: " + taskId);
    }


    protected Model modelFromNativePom( Log log )
            throws MojoExecutionException, MojoFailureException
    {
        Map<String, ModelSource> options = new HashMap<String, ModelSource>();
        options.put( ModelProcessor.SOURCE, new FileModelSource( nativePom ) );

        assert modelManager != null;
        try
        {
            ModelReader reader = modelManager.getReaderFor( options );
            if( reader == null ){
                throw new MojoExecutionException( "no model reader found for " + nativePom );
            }
            if (log.isDebugEnabled()) {
                log.debug( "Parsing native pom " + nativePom );
            }
            return reader.read( nativePom, options );
        }
        catch (IOException e)
        {
            throw new MojoFailureException( "error parsing " + nativePom, e );
        }
    }
}