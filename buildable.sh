#!/bin/bash
#BUILDABLE PROJETCS
if [ -d "/home/custom_workspace3/$1/app/target" ]
then

    #FOR PROJECTS THAT HAVE APP FOLDER
    
    echo 'buildable set to true, publishing project from app/target folder.'
    pwd && cd $1/app/target && pwd && npm publish
    
    cd ../../ && pwd
    echo 'publishing svn tag and upgrading versions...'
    groovy ../versioning.groovy $2 tagTool GDO-6154 $3
    groovy ../versioning.groovy $2 upgradeTool GDO-6154 $3

    cd app && pwd
    echo "upgrading version in the app folder"
    groovy ../../versioning.groovy $2 updateWorkspaceTool GDO-6154 $3
    groovy ../../versioning.groovy $2 upgradeTool GDO-6154 $3
    
    cd ../../ && pwd && rm -rf $1

elif [ -d "/home/custom_workspace3/$1/target" ] 
then

    #FOR PROJECTS WITHOUT APP FOLDER

    echo 'buildable set to true, publishing project from target folder.'
    pwd && cd $1/target && pwd && npm publish
    
    cd ../ && pwd
    echo 'publishing svn tag and upgrading versions...'
    groovy ../versioning.groovy $2 tagTool GDO-6154 $3
    groovy ../versioning.groovy $2 upgradeTool GDO-6154 $3

    cd ../ && pwd && rm -rf $1

else 
    echo "FAILED. There were no target or app/target folders inside project."
fi
