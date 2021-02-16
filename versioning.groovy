// USAGE:
// checkout npm project's svn trunk
// svn co http://svn.g2-networks.net/svn/instantgames/ops/tools/ldeOllieSoftware/commands/standard/trunk/ . 
// call script with svn trunk as first parameter, type of action second parameter, jira issue as third, buildable as fourth
//
// for lde components release: groovy versioning.groovy http://svn.g2-networks.net/svn/instantgames/ops/tools/ldeOllieSoftware/commands/standard/trunk/ releaseComponent GDO-XXXX true/false
// for lde release: groovy versioning.groovy http://svn.g2-networks.net/svn/instantgames/ops/tools/lde/standard/trunk releaseLde GDO-XXXX true/false
// for lde snapshot publish: groovy versioning.groovy http://svn.g2-networks.net/svn/instantgames/ops/tools/lde/standard/trunk publishSnapshotLde GDO-XXXX true/false
// for release of tools not strictly connected to LDE: groovy versioning.groovy [svn location of the trunk folder for the tool] releaseTool GDO-XXXX true/false
// for publishing snapshot of tools not strictly connected to LDE: groovy versioning.groovy [svn location of the trunk folder for the tool] publishSnapshotTool GDO-XXXX true/false
//
//


import static groovy.io.FileType.*
import static groovy.io.FileVisitResult.*
import groovy.json.*

@Grab(group='com.vdurmont', module='semver4j', version='2.2.0')
import com.vdurmont.semver4j.Semver
@Grab(group='com.vdurmont', module='semver4j', version='2.2.0')
import com.vdurmont.semver4j.Semver.SemverType

class SemverNPM extends Semver {

	SemverNPM(String val) {
		super(val, SemverType.NPM)
	}
	
	@Override
    private Semver with(int major, Integer minor, Integer patch, boolean suffix, boolean build) {
        minor = this.minor != null ? minor : null;
        patch = this.patch != null ? patch : null;
        String buildStr = build ? this.build : null;
        String[] suffixTokens = suffix ? this.suffixTokens : null;
        return Semver.create(this.type, major, minor, patch, suffixTokens, buildStr);
    }

    public Semver prevMajor() {
        return with(this.major > 0 ? this.major - 1 : 0, 0, 0, false, false);
    }

    public Semver prevMinor() {
        return with(this.major, this.minor > 0 ? this.minor - 1 : 0, 0, false, false);
    }

    public Semver prevPatch() {
        return with(this.major, this.minor, this.patch > 0 ? this.patch - 1 : 0, false, false);
    }	
}

jira = ""
action = ""
path = ""
buildable = ""
osName = ""

version = ""
initialPackageJSON = {}

cmdPrefix = ""
electronTarget = ""

targetFolder = ""

// if (System.properties['os.name'].toLowerCase().contains('windows')) {
//     cmdPrefix = "cmd /c "
// 	electronTarget = "win"
// } else if (System.properties['os.name'].toLowerCase().contains('linux')) {
// 	cmdPrefix = ""
// 	electronTarget = "win"
// } else {
//     cmdPrefix = "sh -c "
// 	electronTarget = "mac"
// }


println "running with args: " + args

if(args.length == 5) {
	osName = args[4]
	buildable = args[3]
	jira = args[2]
	action = args[1]
	path = args[0]
} else {
	println '''
				Please provide 5 arguments:
				path to the svn trunk, action name, jira issue and buildable
				eg. groovy versioning.groovy http://svn.g2-networks.net/svn/instantgames/ops/tools/lde/standard/trunk/ releaseLde %jira_lde_release_request% true windows
			'''
} 

if (osName == "windows") {
    cmdPrefix = ""
	electronTarget = "win"
} else if (osName == "linux") {
	cmdPrefix = ""
	electronTarget = "mac"
}

switch (action) {
	case "releaseComponent" : releaseComponent(path); break;
}

///--------------------------------ReleasingComponetnt--------------------------------///
// USAGE:
// checkout npm project's svn trunk
// svn co http://svn.g2-networks.net/svn/instantgames/ops/tools/ldeOllieSoftware/commands/standard/trunk/ . 
// call script from trunk folder with svn trunk as first parameter, type of action second parameter, jira issue as third, buildable as fourth
// groovy versioning.groovy http://svn.g2-networks.net/svn/instantgames/ops/tools/ldeOllieSoftware/commands/standard/trunk/ releaseComponent GDO-XXXX true/false
// 


def releaseComponent(String svnPath) {
		
	println "releasing project at svn trunk: " + svnPath
	
	updateWorkspace()
	build()
	publish()
	tag()
	upgrade()
	
	println "finished!"
}

def updateWorkspace() {
	println "updating workspace..."
	try {	
		//def srcDir = new File('./')
		//srcDir.traverse(type: FILES, nameFilter: ~/.*package\.json$/) {
		//println it.path	
		//File f = new File(it.path)
		File f = new File("./package.json")
		def slurper = new JsonSlurper()
		def jsonText = f.getText()
		initialPackageJSON = slurper.parseText( jsonText )
		json = slurper.parseText( jsonText )
		
		println "downgrading dependencies"
		
		if(json.name.contains("@com.igt")) {
			println "upgrading version " //+ it.path
			version = new SemverNPM(json.version.split("-SNAPSHOT")[0]).toString()
			json.version = version
			println "version " + version
			json.dependencies.each { k, v ->
				if(k.contains("@com.igt")) {
					println "---------- > using released dependency    " + k
					def patch = new SemverNPM(v.split("-SNAPSHOT")[0])
					json.dependencies[k] = patch.prevPatch().toString();
				}
			}
			json.devDependencies.each { k, v ->
				if(k.contains("@com.igt")) {
					println "---------- > using released devDependency " + k
					def patch = new SemverNPM(v.split("-SNAPSHOT")[0])
					json.devDependencies[k] = patch.prevPatch().toString()
				}
			}	
			f.write(new JsonBuilder(json).toPrettyString())		
		}
	} catch(Exception ex) {
		throw new Error("couldn't prepare workspace " + ex)
	}	
}

def build() {
	println "starting " + version + " build..."
	try {
		def inst = cmdPrefix + "npm install"
		println inst
		def exInst = inst.execute()
		exInst.in.eachLine {line ->
			println line
		}				
		exInst.waitFor()			
		
		if(exInst.exitValue() == 0) {
			try {
				def build = cmdPrefix + "npm run build:" + electronTarget
				println build
				def exBuild = build.execute()
				exBuild.in.eachLine {line ->
					println line
				}					
				//exBuild.waitFor()
				if(exBuild.exitValue() == 0) {
					println "build successful";
				} else {
					println build + ", exit code: " + build.exitValue()
				}
			} catch(Exception ex) {
				println "couldn't run build command"
			}	
		} else {
			println inst + ", exit code: " + exInst.exitValue()
		}
	} catch(Exception ex) {
		throw new Error("build failed " + ex)
	}	

}

def publish() {
	println "starting publish..."
	try {
		def publish = cmdPrefix + "set NODE_OPTIONS=--max-old-space-size=4096 && npm publish"
		println publish
		def sout = new StringBuilder(), serr = new StringBuilder()
		def proc = publish.execute()
		proc.consumeProcessOutput(sout, serr)
		proc.in.eachLine {line ->
			println line
		}
		proc.waitForOrKill(1000)
		println "out> $sout err> $serr"

		if(proc.exitValue() == 0) {
			println "publish successful!"
		} else {
			println publish + ", exit code: " + proc.exitValue()
		}
	} catch(Exception ex) {
		throw new Error("publish failed " + ex)
	}	 
}

def tag() {
	println "publishing svn tag..."
	try {	
		println "version " + version
		def commStr = cmdPrefix + "svn commit package.json -m \"$jira release $version\""
		def comm = commStr.execute()
		comm.in.eachLine {line ->
			println "svn ci " + line
		}	
		comm.waitFor()	
	
		println "version " + version
		def pathArr = new ArrayList<>(Arrays.asList(path.split("/")))
		println "pathArr " + pathArr
		pathArr.remove(pathArr.size() - 1)
		println "pathArr " + pathArr
		def tagPath = pathArr.join("/") + "/tags/" + version
		println "tagPath " + tagPath	
		def cmd = cmdPrefix + "svn cp --parents " + path + " " + tagPath + " -m \"$jira release $version\""
		println cmd
		def ex = cmd.execute()
		ex.in.eachLine {line ->
			println "svn cp " + line
		}	
		ex.waitFor()
		
		println "tagged"

		// println "updating local tags folder from svn..."
		// def svnUpdate = cmdPrefix + "svn update ../tags/"
		// def exSvnUpdate = svnUpdate.execute()
		// exSvnUpdate.in.eachLine {line ->
		// 	println line
		// }	
		// exSvnUpdate.waitFor()
		
	} catch(Exception ex) {
		throw new Error("publishing svn tag failed " + ex)
	}	
}

def upgrade() {
	println "upgrading versions..."
	try {
		File f = new File("./package.json")
		json = initialPackageJSON
		json.version = new SemverNPM(version).nextPatch().toString() + "-SNAPSHOT"
		f.write(new JsonBuilder(json).toPrettyString())
		println "upgraded version"

		def commStr = cmdPrefix + "svn commit package.json -m \"$jira release $version\""
		def comm = commStr.execute()
		comm.in.eachLine {line ->
			println line
		}	
		comm.waitFor()				

		println "commited upgraded version"			
		
	} catch(Exception ex) {
		throw new Error("upgrading versions failed " + ex)
	}	
}



///--------------------------------ReleasingLDE--------------------------------///
// USAGE:
// checkout npm project's svn trunk
// svn co http://svn.g2-networks.net/svn/instantgames/ops/tools/lde/standard/trunk . 
// call script from trunk folder with svn trunk as first parameter, type of action second parameter, jira issue as third, buildable as fourth
// groovy versioning.groovy http://svn.g2-networks.net/svn/instantgames/ops/tools/lde/standard/trunk releaseLde GDO-XXXX true/false
// 

switch (action) {
	case "releaseLde" : releaseLde(path); break;
}

def releaseLde(String svnPath) {
		
	println "releasing project at svn trunk: " + svnPath
	
	clearLdeDist()
	updateLdeWorkspace()
	tagLde()
	buildReleaseLde()
	publishLde()
	upgradeLde()
	clearLdeDist()
	buildLdeSnapshot()
	publishLdeSnapshot()
	
	println "finished!"
}

def updateLdeWorkspace() {
	println "updating workspace..."
	try {	
		File f = new File("./package.json")
		def slurper = new JsonSlurper()
		def jsonText = f.getText()
		initialPackageJSON = slurper.parseText( jsonText )
		json = slurper.parseText( jsonText )

		def inst = cmdPrefix + "npm install"
		println inst
		def exInst = inst.execute()
		exInst.in.eachLine {line ->
			println line
		}				
		exInst.waitFor()			
		
		if(exInst.exitValue() == 0) {
			
			println "downgrading dependencies"
			
			if(json.name.contains("@com.igt")) {
				println "upgrading version "
				version = new SemverNPM(json.version.split("-SNAPSHOT")[0]).toString()
				json.version = version
				println "version " + version
				json.dependencies.each { k, v ->
					if(k.contains("@com.igt")) {
						println "---------- > using released dependency    " + k
						def patch = new SemverNPM(v.split("-SNAPSHOT")[0])
						json.dependencies[k] = patch.prevPatch().toString();
					}
				}
				json.devDependencies.each { k, v ->
					if(k.contains("@com.igt")) {
						println "---------- > using released devDependency " + k
						def patch = new SemverNPM(v.split("-SNAPSHOT")[0])
						json.devDependencies[k] = patch.prevPatch().toString()
					}
				}	
				f.write(new JsonBuilder(json).toPrettyString())	
			}
			
			println "setting npm config..."
			def npmConfigSet = cmdPrefix + "cd setup/additionalShellScripts && chmod +x npmConfigSet.sh && ./npmConfigSet.sh"
			println npmConfigSet
			def exnpmConfigSet = ["/bin/sh", "-c", npmConfigSet].execute()
			exnpmConfigSet.in.eachLine {line ->
				println line
			}	
			if(exnpmConfigSet.exitValue() == 0) {
				println "npm setup successful";
			} else {
				println update + ", exit code: " + exnpmConfigSet.exitValue()
			}	

			println "upgrading component versions..."
			def upgradeComponents = cmdPrefix + "cd componentsVersion && node downloadLatestVersions.js && node editSettingsJson.js"
			println upgradeComponents
			def exUpgradeComponents = ["/bin/sh", "-c", upgradeComponents].execute()
			exUpgradeComponents.in.eachLine {line ->
				println line
			}	
			if(exUpgradeComponents.exitValue() == 0) {
				println "upgrade successful";
			} else {
				println update + ", exit code: " + exUpgradeComponents.exitValue()
			}	
		}
	} catch(Exception ex) {
		throw new Error("couldn't prepare workspace " + ex)
	}	
}

def tagLde() {
	println "publishing svn tag..."
	try {	
		def commStr = cmdPrefix + "svn commit package.json -m \"$jira release $version\""
		println commStr
		def comm = commStr.execute()
		comm.in.eachLine {line ->
			println "svn ci " + line
		}	
		comm.waitFor()	
	
		println "version " + version
		def pathArr = new ArrayList<>(Arrays.asList(path.split("/")))
		println "pathArr " + pathArr
		pathArr.remove(pathArr.size() - 1)
		println "pathArr " + pathArr
		def tagPath = pathArr.join("/") + "/tags/" + version
		println "tagPath " + tagPath	
		def cmd = cmdPrefix + "svn cp --parents " + path + " " + tagPath + " -m \"$jira release $version\""
		println cmd
		def ex = cmd.execute()
		ex.in.eachLine {line ->
			println "svn cp " + line
		}	
		ex.waitFor()
		
		println "tagged"
		
	} catch(Exception ex) {
		throw new Error("publishing svn tag failed " + ex)
	}	
}

def buildReleaseLde() {
	println "starting " + version + " build..."
	try {
		def inst = cmdPrefix + "npm install"
		println inst
		def exInst = inst.execute()
		exInst.in.eachLine {line ->
			println line
		}				
		exInst.waitFor()			
		
		if(exInst.exitValue() == 0) {
			try {
				def build = cmdPrefix + "npm run build:" + electronTarget 
				println build
				def exBuild = build.execute()
				exBuild.in.eachLine {line ->
					println line
				}					
				//exBuild.waitFor()
				if(exBuild.exitValue() == 0) {
					println "build successful";
				} else {
					println build + ", exit code: " + build.exitValue()
				}
			} catch(Exception ex) {
				println "couldn't run build command"
			}	
		} else {
			println inst + ", FAILED exit code: " + exInst.exitValue()
		}
	} catch(Exception ex) {
		throw new Error("build failed " + ex)
	}	

}

def publishLde() {
	println "starting publish..."
	try {
		def publish = cmdPrefix + "cd dist && set NODE_OPTIONS=--max-old-space-size=4096 && npm publish"
		println publish
		def sout = new StringBuilder(), serr = new StringBuilder()
		def proc = ["/bin/sh", "-c", publish].execute()
		proc.consumeProcessOutput(sout, serr)
		proc.in.eachLine {line ->
			println line
		}
		proc.waitForOrKill(1000)
		println "out> $sout err> $serr"

		if(proc.exitValue() == 0) {
			println "publish successful!"
		} else {
			println publish + ", FAILED exit code: " + proc.exitValue()
		}
	} catch(Exception ex) {
		throw new Error("publish failed " + ex)
	}	 
}

def upgradeLde() {
	println "upgrading versions..."
	try {
		File f = new File("./package.json")
		json = initialPackageJSON
		json.version = new SemverNPM(version).nextPatch().toString() + "-SNAPSHOT"
		f.write(new JsonBuilder(json).toPrettyString())
		version = new SemverNPM(version).nextPatch().toString() + "-SNAPSHOT"
		println "upgraded version to " + version

		def commStr = cmdPrefix + "svn commit package.json -m \"$jira release $version\""
		println commStr
		def comm = commStr.execute()
		comm.in.eachLine {line ->
			println line
		}	 
		comm.waitFor()				

		println "commited upgraded version"	

		println "setting settings.json software paths to placeholder values and editing versions.json... "	
		def setPlaceholders = cmdPrefix + "cd componentsVersion && node setSettingsJsonPlaceholders.js && node editVersionsJson.js"
		def exSetPlaceholders = ["/bin/sh", "-c", setPlaceholders].execute()
		exSetPlaceholders.in.eachLine {line ->
			println line
		}	
		exSetPlaceholders.waitFor()

		if(exSetPlaceholders.exitValue() == 0) {
			println "placeholder values set"
		} else {
			println setPlaceholders + ", FAILED, exit code: " + exSetPlaceholders.exitValue()
		}	
		
	} catch(Exception ex) {
		throw new Error("upgrading versions failed " + ex)
	}	
}

def clearLdeDist() {
	println "cleaning dist folder..."
	try {
		def delete = ""
		if (System.properties['os.name'].toLowerCase().contains('windows')) {
			delete = cmdPrefix + "rmdir /s /q dist"
		} else {
			delete = cmdPrefix + "rm -rf dist"
		}
		println delete
		def exDelete = delete.execute()
		exDelete.in.eachLine {line ->
			println line
		}	
		exDelete.waitFor()

		if(exDelete.exitValue() == 0) {
			println "dist folder deleted"
		} else {
			println delete + ", NOTICE: Dist folder was not deleted., exit code: " + exDelete.exitValue()
		}	
	} catch(Exception ex) {
		throw new Error("delete failed " + ex)
	}	 
}

def buildLdeSnapshot() {
	println "starting " + version + " build..."
	try {
		def inst = cmdPrefix + "npm install"
		println inst
		def exInst = inst.execute()
		exInst.in.eachLine {line ->
			println line
		}				
		exInst.waitFor()			
		
		if(exInst.exitValue() == 0) {
			try {
				def build = cmdPrefix + "npm run build:" + electronTarget
				println build
				def exBuild = build.execute()
				exBuild.in.eachLine {line ->
					println line
				}					
				//exBuild.waitFor()
				if(exBuild.exitValue() == 0) {
					println "build successful";
				} else {
					println build + ", exit code: " + build.exitValue()
				}
			} catch(Exception ex) {
				println "couldn't run build command"
			}	
		} else {
			println inst + ", FAILED exit code: " + exInst.exitValue()
		}
	} catch(Exception ex) {
		throw new Error("build failed " + ex)
	}	

}

def publishLdeSnapshot() {
	println "starting publish for snapshot version..."
	try {
		def publish = cmdPrefix + "cd dist && set NODE_OPTIONS=--max-old-space-size=4096 && npm publish"
		println publish
		def sout = new StringBuilder(), serr = new StringBuilder()
		def proc = ["/bin/sh", "-c", publish].execute()
		proc.consumeProcessOutput(sout, serr)
		proc.in.eachLine {line ->
			println line
		}
		proc.waitForOrKill(1000)
		println "out> $sout err> $serr"

		if(proc.exitValue() == 0) {
			println "publish successful!"
		} else {
			println publish + ", FAILED exit code: " + proc.exitValue()
		}
	} catch(Exception ex) {
		throw new Error("publish failed " + ex)
	}	 
}




///--------------------------------publishingSnapshotLDE--------------------------------///
// USAGE:
// checkout npm project's svn trunk
// svn co http://svn.g2-networks.net/svn/instantgames/ops/tools/lde/standard/trunk . 
// call script from trunk folder with svn trunk as first parameter, type of action second parameter, jira issue as third, buildable as fourth
// groovy versioning.groovy http://svn.g2-networks.net/svn/instantgames/ops/tools/lde/standard/trunk publishSnapshotLde GDO-XXXX true/false
// 


switch (action) {
	case "publishSnapshotLde" : publishSnapshotLde(); break;
}

def publishSnapshotLde() {
		
	println "publishing project to nexus..."
	
	clearLdeTrunk()
	build()
	publishLdeSnapshot()
		
	println "finished!"
}

def clearLdeTrunk() {
	println "deleting dist folder from trunk..."
	try {
		def delete = ""
		if (System.properties['os.name'].toLowerCase().contains('windows')) {
			delete = cmdPrefix + "rmdir /s /q dist"
		} else {
			delete = cmdPrefix + "rm -rf dist"
		}
		println delete
		def exDelete = delete.execute()
		exDelete.in.eachLine {line ->
			println line
		}	
		exDelete.waitFor()

		if(exDelete.exitValue() == 0) {
			println "dist folder deleted from trunk"
		} else {
			println delete + ", NOTICE: Dist folder was not deleted. This may be because there was no dist folder in the trunk before., exit code: " + exDelete.exitValue()
		}	

	} catch(Exception ex) {
		throw new Error("delete failed " + ex)
	}	 
}

///--------------------------------releasingTool--------------------------------///
// USAGE:
// checkout npm project's svn trunk
// svn co http://svn.g2-networks.net/svn/instantgames/ops/tools/ldeOllieSoftware/toolEmu/standard/trunk/ . 
// call script from trunk folder with svn trunk as first parameter, type of action second parameter, jira issue as third, buildable as fourth
// groovy versioning.groovy http://svn.g2-networks.net/svn/instantgames/ops/tools/ldeOllieSoftware/toolEmu/standard/trunk/ releaseTool GDO-XXXX true/false
// 


switch (action) {
	case "updateWorkspaceTool" : updateWorkspaceTool(); break;
	case "buildTool" : buildTool(); break;
	case "tagTool" : tagTool(); break;
	case "upgradeTool" : upgradeTool(); break;
}

//EXECUTE UPDATE
def updateWorkspaceTool(String svnPath) {
	updateWorkspaceTool()
}

//EXECUTE BUILD
def buildTool(String svnPath) {
	buildTool()
}

//EXECUTE TAG
def tagTool(String svnPath) {
	tagTool()
}

//EXECUTE UPGRADE
def upgradeTool(String svnPath) {
	upgradeTool()
}

def updateWorkspaceTool() {
	println "updating workspace..."
	try {	
		//def srcDir = new File('./')
		//srcDir.traverse(type: FILES, nameFilter: ~/.*package\.json$/) {
		//println it.path	
		//File f = new File(it.path)
		File f = new File("./package.json")
		def slurper = new JsonSlurper()
		def jsonText = f.getText()
		initialPackageJSON = slurper.parseText( jsonText )
		json = slurper.parseText( jsonText )
		
		println "downgrading dependencies"
		
		if(json.name.contains("@com.igt")) {
			println "upgrading version " //+ it.path
			version = new SemverNPM(json.version.split("-SNAPSHOT")[0]).toString()
			json.version = version
			println "version " + version
			json.dependencies.each { k, v ->
				if(k.contains("@com.igt")) {
					println "---------- > using released dependency    " + k
					def patch = new SemverNPM(v.split("-SNAPSHOT")[0])
					json.dependencies[k] = patch.prevPatch().toString();
				}
			}
			json.devDependencies.each { k, v ->
				if(k.contains("@com.igt")) {
					println "---------- > using released devDependency " + k
					def patch = new SemverNPM(v.split("-SNAPSHOT")[0])
					json.devDependencies[k] = patch.prevPatch().toString()
				}
			}	
			f.write(new JsonBuilder(json).toPrettyString())		
		}
	} catch(Exception ex) {
		throw new Error("couldn't prepare workspace " + ex)
	}	
}

def buildTool() {
	println "starting $version build..."
	try {
		def inst = cmdPrefix + "npm install"
		println inst
		def exInst = inst.execute()
		exInst.in.eachLine {line ->
			println line
		}				
		exInst.waitFor()			
		
		if(exInst.exitValue() == 0) {
			try {
				def build = cmdPrefix + "npm run build-component"
				println build
				def exBuild = build.execute()
				exBuild.in.eachLine {line ->
					println line
				}					
				exBuild.waitFor()
				if(exBuild.exitValue() == 0) {
					println "build successful";
				} else {
					println build + ", exit code: " + build.exitValue()
				}
			} catch(Exception ex) {
				println "Couldn't run build command, there may not be a build command for this tool."
			}	
		} else {
			println inst + ", exit code: " + exInst.exitValue()
		}
	} catch(Exception ex) {
		throw new Error("build failed " + ex)
	}	

}

def tagTool() {
	println "publishing svn tag..."
	try {	

		File f = new File("./package.json")
		def slurper = new JsonSlurper()
		def jsonText = f.getText()
		initialPackageJSON = slurper.parseText( jsonText )
		json = slurper.parseText( jsonText )
		
		version = new SemverNPM(json.version.split("-SNAPSHOT")[0]).toString()

		println "version " + version
		//def commStr = cmdPrefix + "svn commit package.json -m \'$jira release $version\'"
		def commStr = ["sh", "-c", "svn commit package.json -m \'$jira release $version\'"]
		println commStr
		def comm = commStr.execute()
		comm.in.eachLine {line ->
			println "svn ci " + line
		}	
		comm.waitFor()	
	
		println "version " + version
		def pathArr = new ArrayList<>(Arrays.asList(path.split("/")))
		println "pathArr " + pathArr
		pathArr.remove(pathArr.size() - 1)
		println "pathArr " + pathArr
		def tagPath = pathArr.join("/") + "/tags/" + version
		println "tagPath " + tagPath	
		//def cmd = cmdPrefix + "svn cp --parents " + path + " " + tagPath + " -m \'$jira release $version\'"
		def cmd = ["sh", "-c", "svn cp --parents " + path + " " + tagPath + " -m \'$jira release $version\'"]
		println cmd
		def ex = cmd.execute()
		ex.in.eachLine {line ->
			println "svn cp " + line
		}	
		ex.waitFor()
		
		println "tagged"

	} catch(Exception ex) {
		throw new Error("publishing svn tag failed " + ex)
	}	
}

def upgradeTool() {
	println "upgrading versions..."
	try {
		File f = new File("./package.json")
		def slurper = new JsonSlurper()
		def jsonText = f.getText()
		initialPackageJSON = slurper.parseText( jsonText )
		json = slurper.parseText( jsonText )
		
		version = new SemverNPM(json.version.split("-SNAPSHOT")[0]).toString()

		//File f = new File("./package.json")
		json = initialPackageJSON
		json.version = new SemverNPM(version).nextPatch().toString() + "-SNAPSHOT"	
		f.write(new JsonBuilder(json).toPrettyString())
		println "upgraded version"

		//def commStr = cmdPrefix + "svn commit package.json -m \'$jira release $version\'"
		def commStr = ["sh", "-c", "svn commit package.json -m \'$jira release $version\'"]
		def comm = commStr.execute()
		comm.in.eachLine {line ->
			println line
		}	
		comm.waitFor()				

		println "commited upgraded version"			
		
	} catch(Exception ex) {
		throw new Error("upgrading versions failed " + ex)
	}	
}


///--------------------------------publishingSnapshotTool--------------------------------///
// USAGE:
// checkout npm project's svn trunk
// svn co http://svn.g2-networks.net/svn/instantgames/ops/tools/ldeOllieSoftware/toolEmu/standard/trunk/ . 
// call script from trunk folder with svn trunk as first parameter, type of action second parameter, jira issue as third, buildable as fourth
// groovy versioning.groovy http://svn.g2-networks.net/svn/instantgames/ops/tools/ldeOllieSoftware/toolEmu/standard/trunk/ publishSnapshotTool GDO-XXXX true/false
// 


switch (action) {
	case "publishSnapshotTool" : publishSnapshotTool(); break;
}

def publishSnapshotTool() {
		
	println "publishing project to nexus..."
	
	publish()
		
	println "finished!"
}
























///--------------------------------releasingToolGit--------------------------------///
// USAGE:
// checkout npm project's svn trunk
// svn co http://svn.g2-networks.net/svn/instantgames/ops/tools/ldeOllieSoftware/toolEmu/standard/trunk/ . 
// call script from trunk folder with svn trunk as first parameter, type of action second parameter, jira issue as third, buildable as fourth
// groovy versioning.groovy http://svn.g2-networks.net/svn/instantgames/ops/tools/ldeOllieSoftware/toolEmu/standard/trunk/ releaseToolGit GDO-XXXX true/false
// 


switch (action) {
	case "updateWorkspaceToolGit" : updateWorkspaceToolGit(); break;
	case "buildToolGit" : buildToolGit(); break;
	case "tagToolGit" : tagToolGit(); break;
	case "upgradeToolGit" : upgradeToolGit(); break;
}

//EXECUTE UPDATE
def updateWorkspaceToolGit(String svnPath) {
	updateWorkspaceToolGit()
}

//EXECUTE BUILD
def buildToolGit(String svnPath) {
	buildToolGit()
}

//EXECUTE TAG
def tagToolGit(String svnPath) {
	tagToolGit()
}

//EXECUTE UPGRADE
def upgradeToolGit(String svnPath) {
	upgradeToolGit()
}

def updateWorkspaceToolGit() {
	println "updating workspace..."
	// try {	
	// 	//def srcDir = new File('./')
	// 	//srcDir.traverse(type: FILES, nameFilter: ~/.*package\.json$/) {
	// 	//println it.path	
	// 	//File f = new File(it.path)
	// 	File f = new File("./package.json")
	// 	def slurper = new JsonSlurper()
	// 	def jsonText = f.getText()
	// 	initialPackageJSON = slurper.parseText( jsonText )
	// 	json = slurper.parseText( jsonText )
		
	// 	println "downgrading dependencies"
		
	// 	if(json.name.contains("@com.igt")) {
	// 		println "upgrading version " //+ it.path
	// 		version = new SemverNPM(json.version.split("-SNAPSHOT")[0]).toString()
	// 		json.version = version
	// 		println "version " + version
	// 		json.dependencies.each { k, v ->
	// 			if(k.contains("@com.igt")) {
	// 				println "---------- > using released dependency    " + k
	// 				def patch = new SemverNPM(v.split("-SNAPSHOT")[0])
	// 				json.dependencies[k] = patch.prevPatch().toString();
	// 			}
	// 		}
	// 		json.devDependencies.each { k, v ->
	// 			if(k.contains("@com.igt")) {
	// 				println "---------- > using released devDependency " + k
	// 				def patch = new SemverNPM(v.split("-SNAPSHOT")[0])
	// 				json.devDependencies[k] = patch.prevPatch().toString()
	// 			}
	// 		}	
	// 		f.write(new JsonBuilder(json).toPrettyString())		
	// 	}
	// } catch(Exception ex) {
	// 	throw new Error("couldn't prepare workspace " + ex)
	// }	
}

def buildToolGit() {
	println "starting $version build..."
	// try {
	// 	def inst = cmdPrefix + "npm install"
	// 	println inst
	// 	def exInst = inst.execute()
	// 	exInst.in.eachLine {line ->
	// 		println line
	// 	}				
	// 	exInst.waitFor()			
		
	// 	if(exInst.exitValue() == 0) {
	// 		try {
	// 			def build = cmdPrefix + "npm run build-component"
	// 			println build
	// 			def exBuild = build.execute()
	// 			exBuild.in.eachLine {line ->
	// 				println line
	// 			}					
	// 			exBuild.waitFor()
	// 			if(exBuild.exitValue() == 0) {
	// 				println "build successful";
	// 			} else {
	// 				println build + ", exit code: " + build.exitValue()
	// 			}
	// 		} catch(Exception ex) {
	// 			println "Couldn't run build command, there may not be a build command for this tool."
	// 		}	
	// 	} else {
	// 		println inst + ", exit code: " + exInst.exitValue()
	// 	}
	// } catch(Exception ex) {
	// 	throw new Error("build failed " + ex)
	// }	

}

def tagToolGit() {
	println "publishing svn tag..."
	// try {	

	// 	File f = new File("./package.json")
	// 	def slurper = new JsonSlurper()
	// 	def jsonText = f.getText()
	// 	initialPackageJSON = slurper.parseText( jsonText )
	// 	json = slurper.parseText( jsonText )
		
	// 	version = new SemverNPM(json.version.split("-SNAPSHOT")[0]).toString()

	// 	println "version " + version
	// 	//def commStr = cmdPrefix + "svn commit package.json -m \'$jira release $version\'"
	// 	def commStr = ["sh", "-c", "svn commit package.json -m \'$jira release $version\'"]
	// 	println commStr
	// 	def comm = commStr.execute()
	// 	comm.in.eachLine {line ->
	// 		println "svn ci " + line
	// 	}	
	// 	comm.waitFor()	
	
	// 	println "version " + version
	// 	def pathArr = new ArrayList<>(Arrays.asList(path.split("/")))
	// 	println "pathArr " + pathArr
	// 	pathArr.remove(pathArr.size() - 1)
	// 	println "pathArr " + pathArr
	// 	def tagPath = pathArr.join("/") + "/tags/" + version
	// 	println "tagPath " + tagPath	
	// 	//def cmd = cmdPrefix + "svn cp --parents " + path + " " + tagPath + " -m \'$jira release $version\'"
	// 	def cmd = ["sh", "-c", "svn cp --parents " + path + " " + tagPath + " -m \'$jira release $version\'"]
	// 	println cmd
	// 	def ex = cmd.execute()
	// 	ex.in.eachLine {line ->
	// 		println "svn cp " + line
	// 	}	
	// 	ex.waitFor()
		
	// 	println "tagged"

	// } catch(Exception ex) {
	// 	throw new Error("publishing svn tag failed " + ex)
	// }	
}

def upgradeToolGit() {
	println "upgrading versions..."
	// try {
	// 	File f = new File("./package.json")
	// 	def slurper = new JsonSlurper()
	// 	def jsonText = f.getText()
	// 	initialPackageJSON = slurper.parseText( jsonText )
	// 	json = slurper.parseText( jsonText )
		
	// 	version = new SemverNPM(json.version.split("-SNAPSHOT")[0]).toString()

	// 	//File f = new File("./package.json")
	// 	json = initialPackageJSON
	// 	json.version = new SemverNPM(version).nextPatch().toString() + "-SNAPSHOT"	
	// 	f.write(new JsonBuilder(json).toPrettyString())
	// 	println "upgraded version"

	// 	//def commStr = cmdPrefix + "svn commit package.json -m \'$jira release $version\'"
	// 	def commStr = ["sh", "-c", "svn commit package.json -m \'$jira release $version\'"]
	// 	def comm = commStr.execute()
	// 	comm.in.eachLine {line ->
	// 		println line
	// 	}	
	// 	comm.waitFor()				

	// 	println "commited upgraded version"			
		
	// } catch(Exception ex) {
	// 	throw new Error("upgrading versions failed " + ex)
	// }	
}
