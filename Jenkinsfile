#!/usr/bin/env groovy
pipeline{
    agent any

    //Define stages for the build process
    stages{
        //Define the deploy stage
        stage('Deploy'){
            steps{
                script{
                    docker.withRegistry('https://gt-build.hdap.gatech.edu'){
                        //Build and push the database image
                        def elrReceiverImage = docker.build("elrreceiver:${env.BUILD_NUMBER}", "-f ./Dockerfile .")
                        elrReceiverImage.push("${env.BUILD_NUMBER}")
                    }
                }
                script{
                    docker.withRegistry('https://797827902844.dkr.ecr.us-east-2.amazonaws.com', 'ecr:us-east-2:open-mdi-credential'){
                        //Build and push the database image
                        def openMdiImage = docker.build("om-java-ms-gtri-elr-receiver:${env.BUILD_NUMBER}", "-f ./Dockerfile .")
                        openMdiImage.push("${env.BUILD_NUMBER}")
                    }
                }
            }
        }

        //Define stage to notify rancher
        stage('Notify'){
            steps{
                script{
                    rancher confirm: true, credentialId: 'gt-rancher-server', endpoint: 'https://gt-rancher.hdap.gatech.edu/v2-beta', environmentId: '1a7', environments: '', image: "gt-build.hdap.gatech.edu/elrreceiver:${env.BUILD_NUMBER}", ports: '', service: 'GPHD/elr-receiver', timeout: 60
                    rancher confirm: true, credentialId: 'gt-rancher-server', endpoint: 'https://gt-rancher.hdap.gatech.edu/v2-beta', environmentId: '1a7', environments: '', image: "gt-build.hdap.gatech.edu/elrreceiver:${env.BUILD_NUMBER}", ports: '', service: 'MortalityReporting/elr-receiver', timeout: 60
                }
            }
        }
    }
}
