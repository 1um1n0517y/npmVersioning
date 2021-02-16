pipeline {
    agent { 
        node {
            label 'slave_05-24b648a0'
            customWorkspace '/home/custom_workspace3'
        }
    }

    parameters {
        string ( name: 'toolName', 
                defaultValue: '', 
                description: 'Name of the tool ex. "toolEmu"')
        string ( name: 'toolSvnPath',
                defaultValue: '',
                description: 'Use svn path like "http://svn.g2-networks.net/svn/instantgames/tools/emu/standard/imp/trunk/"')
        booleanParam ( name: 'buildable', 
                defaultValue: false, 
                description: 'Used for components that are built into target folder')
        string ( name: 'jiraIssue', 
                defaultValue: 'GDO-6154', 
                description: '')
    }

    stages {
        stage ('Checkouting scripts') {
            steps {
                sh "svn co http://svn.g2-networks.net/svn/instantgames/ops/tools/npmVersioning ."
            }
        }
        stage ('Setting up npm config') {
            steps {
                sh 'npm config set "@com.igt.gi.lde:registry=http://nexus-04/nexus/repository/npm-host/" && npm config set "@com.igt.gi.lde.standard:registry=http://nexus-04/nexus/repository/npm-host/" && npm config set "@com.igt.gi.lde.software.standard:registry=http://nexus-04/nexus/repository/npm-host/" && npm config set "@com.igt.gi.tools.standard:registry=http://nexus-04/nexus/repository/npm-host/" && npm config set "@com.igt.gi.tools.macos:registry=http://nexus-04/nexus/repository/npm-host/" && npm config set "@com.igt.gi.lde.software.macos:registry=http://nexus-04/nexus/repository/npm-host/" && npm config set "@com.igt.gi.lde.macos:registry=http://nexus-04/nexus/repository/npm-host/"'
            }
        }
        stage ('Checkouting tool') {
            steps {
                echo "checkouting " + params.toolName + "..."
                sh "svn co ${params.toolSvnPath} ${params.toolName}"
            }
        }
        stage ('Updating workspace and building project') {
            steps {
                sh "cd ${params.toolName} && groovy ../versioning.groovy ${params.toolSvnPath} updateWorkspaceTool GDO-6154 ${params.buildable} && groovy ../versioning.groovy ${params.toolSvnPath} buildTool GDO-6154 ${params.buildable}"
                sh "if [ ${params.buildable} == true ]; then chmod 744 buildable.sh; ./buildable.sh ${params.toolName} ${params.toolSvnPath} ${params.buildable};  else chmod 744 non-buildable.sh; ./non-buildable.sh ${params.toolName} ${params.toolSvnPath} ${params.buildable}; fi;" 
                sh "rm -rf ${params.toolName}"              
            }
        }
    }
}
