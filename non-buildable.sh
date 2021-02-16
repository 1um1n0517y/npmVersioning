#NON-BUILDABLE PROJECTS
	
	echo 'buildable set to false, publishing project from root folder.'
    cd $1 && pwd && npm publish
    
    echo 'publishing svn tag and upgrading versions...'
    groovy ../versioning.groovy $2 tagTool GDO-6154 $3
    groovy ../versioning.groovy $2 upgradeTool GDO-6154 $3

	if [ -d "/home/custom_workspace3/$1/app" ] 
    then
    	cd app && pwd
        echo "upgrading version in the app folder"
    	groovy ../../versioning.groovy $2/app updateWorkspaceTool GDO-6154 $3
    	groovy ../../versioning.groovy $2/app upgradeTool GDO-6154 $3
    fi
    
    cd ../../ && pwd && rm -rf $1