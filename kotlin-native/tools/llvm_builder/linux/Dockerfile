FROM ubuntu:16.04

ENV TZ=Europe/Moscow
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

RUN apt-get update
RUN apt-get -y install software-properties-common
RUN add-apt-repository -y ppa:fkrull/deadsnakes
RUN apt-get update
RUN apt-get install -y python3.6 python3-dev
RUN apt-get install -y gcc g++
RUN apt-get install -y git cmake curl unzip ninja-build
RUN apt-get install -y build-essential

# Create a user.
ARG USERNAME=jb
RUN groupadd -g 1000 $USERNAME
RUN useradd -r -u 1000 --create-home -g $USERNAME $USERNAME
USER $USERNAME
WORKDIR /home/$USERNAME

COPY ../package.py .

ENV NAME="llvm-distribution"

ENTRYPOINT "python3.6" "package.py" "--install-path" /artifacts/${NAME} "--archive-path" /artifacts/${NAME}
